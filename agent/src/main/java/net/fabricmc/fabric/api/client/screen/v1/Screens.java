package net.fabricmc.fabric.api.client.screen.v1;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Adaptateur Irium — accès aux widgets d'un Screen (fabric-screen-api-v1).
 * M7-X2 : Screens.getWidgets référencé par Mod Menu / JEI / MouseTweaks.
 */
public final class Screens {
    private Screens() {}

    @SuppressWarnings("unchecked")
    public static List<AbstractWidget> getWidgets(Screen screen) {
        try {
            var children = screen.children();
            java.util.List<AbstractWidget> out = new java.util.ArrayList<>();
            for (Object c : children) {
                if (c instanceof AbstractWidget w) out.add(w);
            }
            return out;
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }
}
