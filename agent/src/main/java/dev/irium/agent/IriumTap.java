package dev.irium.agent;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * M3 : le tap Irium — greffé addBefore("decoder") sur la pipeline netty du host.
 *
 * INVARIANTS :
 *  - Peek-only : lit les octets via duplicate(), ne consomme JAMAIS le buffer,
 *    retransmet chaque message tel quel (refcount intact). Le host ne peut pas
 *    détecter une différence de comportement réseau.
 *  - Zéro dépendance Mojang : ne voit que des ByteBuf décompressés post-framing.
 *    Insensible à l'obfuscation du client (les noms splitter/decoder/packet_handler
 *    sont des constantes de chaîne netty/MC stables).
 *  - Émet via channel.writeAndFlush(buf brut id+corps) : traversera l'encoder/
 *    compresseur du host (MessageToByteEncoder ignore les types non appariés).
 *
 * Machine d'état (observation) : login -> configuration -> play.
 * Actions : à l'entrée PLAY -> minecraft:register irium:hello ; à la réception
 * du challenge irium -> réponse signée par l'echo du nonce.
 *
 * IDs utilisés = protocole 776 (26.2). Le M4 remplacera ces constantes par une
 * table de version résolue via le handshake initial (protocol version).
 */
public final class IriumTap extends ChannelInboundHandlerAdapter {

    public static final String NAME = "irium_tap";
    private static final String CHANNEL = "irium:hello";
    private static final String MODULE_CHANNEL = dev.irium.agent.module.ModuleManager.CHANNEL;

    private enum State { LOGIN, CONFIGURATION, PLAY_PENDING, PLAY }

    private State state = State.LOGIN;
    private boolean registered = false;
    private final AtomicReference<ChannelHandlerContext> ctxRef = new AtomicReference<>();
    private static final AtomicReference<Channel> CURRENT = new AtomicReference<>();

    IriumTap() {}
    private IriumTap(State state, boolean registered) {
        this.state = state;
        this.registered = registered;
    }

    /** Canal de la session courante (émission de payloads mods). */
    public static Channel currentChannel() { return CURRENT.get(); }

    /* ---------------- installation ---------------- */

    static void install(ChannelPipeline p) {
        if (p.get("decoder") == null) {
            // Les connexions MC réelles ajoutent parfois packet_handler AVANT
            // decoder (ordre non garanti selon la version) : on attend decoder
            // au lieu d'abandonner. install() est rappelé à chaque handler ajouté.
            return; // silencieux : rappelé à chaque addLast/addBefore nommé
        }
        if (p.get(NAME) != null) {
            // déjà installé : repositionner UNIQUEMENT si un handler s'est
            // glissé entre le tap et decoder (ex: decompress ajouté après coup)
            var names = p.names();
            for (int i = 0; i < names.size() - 1; i++) {
                if (names.get(i).equals(NAME) && names.get(i + 1).equals("decoder")) {
                    return; // déjà bien placé, rien à faire
                }
            }
        }
        // ancre : TOUJOURS 'decoder'. decompress est toujours immédiatement avant
        // decoder -> addBefore("decoder") place le tap APRÈS decompress quand il
        // existe (trames décompressées), et à la bonne place sinon. Quand MC ajoute
        // 'decompress', le hook re-fire -> on retire et réinsère le tap.
        String anchor = "decoder";
        boolean reposition = p.get(NAME) != null;
        if (reposition) {
            try {
                IriumTap old = (IriumTap) p.get(NAME);
                p.remove(NAME);
                p.addBefore(anchor, NAME, new IriumTap(old.state, old.registered)); // l'état survit au déplacement
            } catch (Throwable ignored) {}
        } else {
            p.addBefore(anchor, NAME, new IriumTap());
        }
        IriumAgent.log("[tap] " + (reposition ? "repositionné" : "installé") + " avant '" + anchor + "' sur "
                + p.channel() + " (ordre: " + p.names() + ")");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctxRef.set(ctx);
    }

    /* ---------------- observation ---------------- */

    /** Re-fire JOIN après l'installation tardive d'un mod streamé. */
    public static void fireJoinLate() { fireJoin(); }

    /**
     * M7-B11c : self-test Mod Menu — reproduit le crash user (clic bouton Mods :
     * ModsScreen.init -> ModListWidget.filter -> updateSelectedEntry ->
     * getContact()/getIcon()) sans interaction humaine. Activé par
     * -Dirium.test.modsscreen=true (bot/harnais). Ouvre et referme l'écran.
     */
    public static void selfTestModsScreen() {
        if (!Boolean.getBoolean("irium.test.modsscreen")) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(6000); // laisser le HUD/minimap s'installer
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        Class<?> scr = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                        java.lang.reflect.Constructor<?> ctor = scr.getConstructor(
                                net.minecraft.client.gui.screens.Screen.class);
                        mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) ctor.newInstance(
                                (net.minecraft.client.gui.screens.Screen) null));
                        dev.irium.agent.IriumAgent.log(
                                "[self-test] ModsScreen OUVERT — si getContact/getIcon cassent, FATAL ici");
                        Thread.sleep(2500); // 3 ticks min pour init + render + filter
                        mc.gui.setScreen(null);
                        dev.irium.agent.IriumAgent.log("[self-test] ModsScreen refermé SANS crash");
                    } catch (Throwable t2) {
                        dev.irium.agent.IriumAgent.log("[self-test] ModsScreen échec: " + t2);
                    }
                });
            } catch (Throwable ignored) {}
        }, "irium-selftest");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        dev.irium.agent.module.ModuleManager.close(ctx.channel()); // sandbox : tout retombe
        if (CURRENT.compareAndSet(ctx.channel(), null)) {
            fireDisconnect();
        }
        super.channelInactive(ctx);
    }

    /** Fabric JOIN/DISCONNECT — portés par le tap, jamais d'exception. */
    public static void fireJoin() {
        selfTestModsScreen();
        try {
            Object invoker = net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.invoker();
            if (invoker != null) ((net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join) invoker)
                    .onPlayReady(null, null, net.minecraft.client.Minecraft.getInstance());
        } catch (Throwable ignored) {}
    }

    public static void fireDisconnect() {
        try {
            Object invoker = net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.invoker();
            if (invoker != null) ((net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect) invoker)
                    .onPlayDisconnect(null, null, net.minecraft.client.Minecraft.getInstance());
        } catch (Throwable ignored) {}
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof ByteBuf buf) {
                peek(ctx, buf);
            }
        } catch (Throwable t) {
            IriumAgent.log("[tap] erreur d'observation (ignorée, host intact) : " + t);
        } finally {
            ctx.fireChannelRead(msg); // JAMAIS avaler
        }
    }

    private void peek(ChannelHandlerContext ctx, ByteBuf buf) {
        ByteBuf d = buf.duplicate(); // lecture sans consommer
        if (!d.isReadable()) return;
        int[] r = varIntAt(d, d.readerIndex());
        int pid = r[0];
        if (IriumAgent.DEBUG) IriumAgent.log("[tap:peek] state=" + state + " pid=0x" + Integer.toHexString(pid) + " len=" + d.readableBytes());
        switch (state) {
            case LOGIN -> {
                if (pid == 0x02) state = State.CONFIGURATION;          // game_profile -> config
                else if (pid == 0x00 && r[1] < d.readableBytes()) {
                    // fenêtre compression : trame [0][paquet] vue avant l'installation
                    // de decompress -> le vrai pid est à +1 (game_profile enrobée)
                    int inner = varIntAt(d, r[1])[0];
                    if (inner == 0x02) state = State.CONFIGURATION;
                }
                else if (pid == 0x0e && d.readableBytes() <= 40) {
                    state = State.CONFIGURATION;                        // auto-réparation : select_known_packs
                }
            }
            case CONFIGURATION -> {
                if (pid == 0x03) {                                     // finish -> attente
                    state = State.PLAY_PENDING;
                    // filet de sécurité : si le serveur ne dit plus rien en 3s,
                    // on tente le register quand même (ne devrait jamais arriver :
                    // un serveur PLAY émet des paquets immédiatement).
                    ctx.channel().eventLoop().schedule(() -> {
                        if (state == State.PLAY_PENDING) onPlay(ctx);
                    }, 3, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
            case PLAY_PENDING -> {
                // Le serveur n'émet un paquet PLAY qu'APRÈS avoir décodé notre
                // acknowledge_configuration (sinon il violerait son propre
                // protocole). Ce paquet prouve donc que le canal est PLAY côté
                // serveur : le register ne peut plus le faire kicker.
                onPlay(ctx);
                state = State.PLAY;
                if (pid == 0x18) onCustomPayload(ctx, d, r[1]);
            }
            case PLAY -> {
                if (pid == 0x18) onCustomPayload(ctx, d, r[1]);        // custom_payload clientbound
            }
        }
    }

    /* ---------------- actions ---------------- */

    private void onPlay(ChannelHandlerContext ctx) {
        IriumAgent.log("[tap] PLAY atteint sur " + ctx.channel());
        CURRENT.set(ctx.channel());
        fireJoin();
        if (registered) return;
        registered = true;
        // minecraft:register : custom_payload serverbound PLAY = 0x16, corps = canaux \0-séparés
        ByteBuf reg = Unpooled.buffer();
        writeVarInt(reg, 0x16);
        writeString(reg, "minecraft:register");
        reg.writeBytes((CHANNEL + "\0" + MODULE_CHANNEL).getBytes(StandardCharsets.UTF_8));
        ctx.channel().writeAndFlush(reg);
        IriumAgent.log("[tap] minecraft:register " + CHANNEL + " + " + MODULE_CHANNEL + " envoyé");
    }

    private void onCustomPayload(ChannelHandlerContext ctx, ByteBuf d, int offset) {
        d.readerIndex(offset);
        String chan = readString(d);
        if (MODULE_CHANNEL.equals(chan)) {
            dev.irium.agent.module.ModuleManager.of(ctx.channel()).ingest(d);
            return;
        }
        // M7-B : dispatch des custom_payload vers les mods Fabric streamés
        try {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(chan);
            if (!chan.startsWith("irium:") && !chan.equals("minecraft:register")
                    && !chan.equals("minecraft:unregister")) {
                byte[] body = new byte[d.readableBytes()];
                d.readBytes(body);
                net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.dispatch(id, body);
            }
        } catch (Throwable ignored) {
            // canal non Identifier ou pas de handler : on ignore, le host voit tout
        }
        if (!CHANNEL.equals(chan)) return;
        int bodyLen = d.readableBytes();
        if (bodyLen < 12) return;
        int ridx = d.readerIndex();
        if (d.getByte(ridx) != 'I' || d.getByte(ridx + 1) != 'R') return;
        if (d.getByte(ridx + 2) != 1 || d.getByte(ridx + 3) != 0x01) return; // version, TYPE_HELLO
        long nonce = 0;
        for (int i = 0; i < 8; i++) nonce = (nonce << 8) | (d.getByte(ridx + 4 + i) & 0xFF);

        IriumAgent.log("[tap] CHALLENGE irium nonce=0x" + Long.toHexString(nonce));

        // réponse : IR + v1 + TYPE_AGENT_RESPONSE + nonce(8) + len(2) + "0.3.0" + caps
        byte[] ver = "0.4.0".getBytes(StandardCharsets.UTF_8);
        ByteBuf resp = Unpooled.buffer();
        writeVarInt(resp, 0x16);
        writeString(resp, CHANNEL);
        resp.writeByte('I'); resp.writeByte('R'); resp.writeByte(1); resp.writeByte(0x02);
        for (int i = 0; i < 8; i++) resp.writeByte((byte) (nonce >>> (56 - 8 * i)));
        resp.writeShort(ver.length);
        resp.writeBytes(ver);
        resp.writeByte(0x1F);
        ctx.channel().writeAndFlush(resp);
        IriumAgent.log("[tap] réponse AGENT envoyée (0.4.0, caps=0x1F)");
    }

    /* ---------------- utilitaires octets ---------------- */

    private static void writeVarInt(ByteBuf b, int v) {
        while ((v & 0xFFFFFF80) != 0) { b.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        b.writeByte(v);
    }

    private static void writeString(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(b, x.length);
        b.writeBytes(x);
    }

    private static String readString(ByteBuf d) {
        int[] r = varIntAt(d, d.readerIndex());
        d.readerIndex(r[1]);
        return d.readCharSequence(r[0], StandardCharsets.UTF_8).toString();
    }

    private static int[] varIntAt(ByteBuf d, int o) {
        int v = 0, sh = 0, p = o;
        while (true) {
            int b = d.getByte(p++) & 0xFF;
            v |= (b & 0x7F) << sh;
            if ((b & 0x80) == 0) return new int[]{v, p};
            sh += 7;
        }
    }
}
