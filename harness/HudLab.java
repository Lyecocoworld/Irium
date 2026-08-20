import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * LABO M5 - client NETTY "sans aucune connaissance d'Irium", AVEC hot-path.
 *
 * Copie conforme de VanillaNoIq (protocole 776 complet) mais possède un
 * Ticker : tick() appelé 20x/s sur un thread dédié. C'est la cible de la
 * recette M5 — le hook s'y injecte sans que Ticker connaisse Irium.
 * Test A/B : même binaire, seule différence = présence du -javaagent.
 */
public final class HudLab {

    /** Hot-path simulée : l'équivalent labo d'InGameHud.render(). */
    public static final class Ticker {
        private volatile boolean running = true;

        public void start() {
            Thread t = new Thread(() -> {
                while (running) {
                    tick();
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                }
            }, "render-sim");
            t.setDaemon(true);
            t.start();
            System.err.println("[hudlab] Ticker démarré (20 tps simulé)");
        }

        public void stop() { running = false; }

        /** CIBLE DE LA RECETTE : hook injecté avant le RETURN unique. */
        public void tick() {
        }
    }

    public static void main(String[] args) throws Exception {
        System.err.println("[hudlab] demarrage - client sans Irium, avec hot-path tick()");
        Ticker ticker = new Ticker();
        ticker.start();
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // ordre head->tail : splitter, sizer, compress, decoder, packet_handler
                            // (outbound part de la queue : compress cadre, puis sizer mesure)
                            p.addLast("splitter", new VarintSplitter());
                            Compressor compress = new Compressor();
                            p.addLast("sizer", new Sizer());
                            p.addLast("compress", compress);
                            p.addLast("decoder", new ProtoHandler(compress));
                            p.addLast("packet_handler", new ChannelInboundHandlerAdapter());
                        }
                    });
            Channel ch = b.connect("127.0.0.1", 25599).sync().channel();
            System.err.println("[hudlab] connecte : " + ch);

            // --- handshake intention (etat HANDSHAKING, id 0) ---
            ByteBuf hs = ch.alloc().buffer();
            varInt(hs, 0);
            varInt(hs, 776);
            str(hs, "127.0.0.1");
            hs.writeShort(25599);
            varInt(hs, 2); // next state = login
            ch.writeAndFlush(wrap(hs));

            // --- login start (id 0) : nom + UUID (protocole 776) ---
            ByteBuf ls = ch.alloc().buffer();
            varInt(ls, 0);
            str(ls, "HudLab");
            UUID u = UUID.nameUUIDFromBytes("OfflinePlayer:HudLab".getBytes(StandardCharsets.UTF_8));
            ls.writeLong(u.getMostSignificantBits());
            ls.writeLong(u.getLeastSignificantBits());
            ch.writeAndFlush(wrap(ls));

            // rester connecte 20 s — la recette doit pouvoir hooker Ticker.tick()
            ch.closeFuture().await(20000, TimeUnit.MILLISECONDS);
            System.err.println("[hudlab] fin de session");
        } finally {
            ticker.stop();
            group.shutdownGracefully();
        }
    }

    /* ================= machine a etats protocole (le "vrai client") ================= */

    static final class ProtoHandler extends ChannelInboundHandlerAdapter {
        enum S { LOGIN, CONFIG, PLAY }
        S s = S.LOGIN;
        final Compressor compress;

        ProtoHandler(Compressor compress) { this.compress = compress; }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf d = (ByteBuf) msg;
            try {
                int[] r = varIntAt(d, d.readerIndex());
                int pid = r[0];
                switch (s) {
                    case LOGIN -> {
                        if (pid == 0x03 && d.readableBytes() <= 6) {           // set_compression
                            int threshold = varIntAt(d, r[1])[0];
                            compress.threshold = threshold;
                            ctx.pipeline().addBefore("decoder", "decompress", new Decompressor());
                        } else if (pid == 0x02) {                              // login_success
                            write(ctx, 0x03, w -> {});                          // login_ack
                            s = S.CONFIG;
                        }
                    }
                    case CONFIG -> {
                        if (pid == 0x0e) {                                      // select_known_packs
                            write(ctx, 0x07, w -> varInt(w, 0));                // reponse : 0 pack
                        } else if (pid == 0x04) {                               // keep_alive config
                            long id = varLongAt(d, r[1]);
                            write(ctx, 0x04, w -> varLong(w, id));
                        } else if (pid == 0x03) {                               // finish
                            write(ctx, 0x03, w -> {});                          // finish_ack
                            s = S.PLAY;
                        }
                    }
                    case PLAY -> {                                              // keep_alive play (id ~0x21)
                        if (r[1] < d.readableBytes() && d.readableBytes() <= 10) {
                            long id = varLongAt(d, r[1]);
                            if (id > 1_500_000_000_000L) {
                                write(ctx, 0x1C, w -> varLong(w, id));
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[hudlab] erreur proto (ignoree) : " + t);
            } finally {
                d.release(); // consommateur terminal
            }
        }

        interface W { void write(ByteBuf w); }

        static void write(ChannelHandlerContext ctx, int pid, W body) {
            ByteBuf out = Unpooled.buffer();
            varInt(out, pid);
            body.write(out);
            ctx.channel().writeAndFlush(out);
        }
    }

    /* ================= codecs reseau (identiques en nature a ceux de MC) ================= */

    /** framing MC : longueur VarInt (1-5 octets) puis payload. */
    static final class VarintSplitter extends io.netty.handler.codec.ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            in.markReaderIndex();
            int len = 0, sh = 0;
            while (true) {
                if (!in.isReadable()) { in.resetReaderIndex(); return; }
                int b = in.readUnsignedByte();
                len |= (b & 0x7F) << sh;
                if ((b & 0x80) == 0) break;
                sh += 7;
                if (sh > 28) throw new DecoderException("longueur varint invalide");
            }
            if (in.readableBytes() < len) { in.resetReaderIndex(); return; }
            out.add(in.readRetainedSlice(len));
        }
    }

    /** decompress inbound : [tailleNonCompressee varint][zlib] -> trames pleines */
    static final class Decompressor extends MessageToMessageDecoder<ByteBuf> {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
            int[] r = varIntAt(buf, buf.readerIndex());
            int size = r[0];
            buf.readerIndex(r[1]);
            if (size == 0) {
                out.add(buf.readRetainedSlice(buf.readableBytes()));
                return;
            }
            byte[] z = new byte[buf.readableBytes()];
            buf.readBytes(z);
            Inflater inf = new Inflater();
            inf.setInput(z);
            byte[] res = new byte[size];
            try {
                inf.inflate(res);
            } finally {
                inf.end();
            }
            out.add(Unpooled.wrappedBuffer(res));
        }
    }

    /** compress outbound : [0 varint][data] si sous seuil, [taille][zlib] sinon */
    static final class Compressor extends MessageToByteEncoder<ByteBuf> {
        volatile int threshold = -1;

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
            int len = in.readableBytes();
            if (threshold < 0) {
                // compression non negociee : pas de prefixe du tout (format [len][id][body])
                out.writeBytes(in);
            } else if (len >= threshold) {
                byte[] raw = new byte[len];
                in.readBytes(raw);
                Deflater def = new Deflater();
                def.setInput(raw);
                def.finish();
                byte[] tmp = new byte[len + 64];
                int n = def.deflate(tmp);
                def.end();
                varInt(out, len);
                out.writeBytes(tmp, 0, n);
            } else {
                varInt(out, 0);
                out.writeBytes(in);
            }
        }
    }

    /** sizer outbound : prefice la longueur totale (varint) */
    static final class Sizer extends MessageToByteEncoder<ByteBuf> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
            varInt(out, in.readableBytes());
            out.writeBytes(in);
        }
    }

    /* ================= utilitaires ================= */

    private static Object wrap(ByteBuf body) {
        // avant set_compression le client n'a pas encore de compress actif :
        // sizer prefice la longueur -> format [len][id][body]
        return body;
    }

    static void varInt(ByteBuf b, int v) {
        while ((v & 0xFFFFFF80) != 0) { b.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        b.writeByte(v);
    }

    static void varLong(ByteBuf b, long v) {
        while ((v & 0xFFFFFFFFFFFFFF80L) != 0) { b.writeByte((int) ((v & 0x7F) | 0x80)); v >>>= 7; }
        b.writeByte((int) v);
    }

    static void str(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        varInt(b, x.length);
        b.writeBytes(x);
    }

    static int[] varIntAt(ByteBuf d, int o) {
        int v = 0, sh = 0, p = o;
        while (true) {
            int b = d.getByte(p++) & 0xFF;
            v |= (b & 0x7F) << sh;
            if ((b & 0x80) == 0) return new int[]{v, p};
            sh += 7;
        }
    }

    static long varLongAt(ByteBuf d, int o) {
        long v = 0;
        int sh = 0, p = o;
        while (true) {
            int b = d.getByte(p++) & 0xFF;
            v |= (long) (b & 0x7F) << sh;
            if ((b & 0x80) == 0) return v;
            sh += 7;
        }
    }
}
