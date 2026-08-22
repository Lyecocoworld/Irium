package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;

/**
 * M7-X21 : join direct depuis le titre Irium. Invoke reflection légère sur
 * ConnectScreen.startConnecting — signature 26.2 confirmée par javap :
 * startConnecting(Screen parent, Minecraft mc, ServerAddress addr,
 * ServerData data, boolean quickJoin, TransferState ts).
 */
public final class IriumConnect {

    private IriumConnect() {
    }

    public static void connect(Minecraft mc, TitleScreen parent, String hostPort) {
        try {
            ServerAddress addr = ServerAddress.parseString(hostPort);
            ServerData data = new ServerData("Irium", hostPort, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(parent, mc, addr, data, false, null);
        } catch (Throwable t) {
            SafeLog.offer("[gateway] join direct échoué: " + t);
        }
    }

    public static void openOptions(Minecraft mc, TitleScreen parent) {
        try {
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.options.OptionsScreen(parent, mc.options, false));
        } catch (Throwable t) {
            SafeLog.offer("[gateway] options échoué: " + t);
        }
    }
}
