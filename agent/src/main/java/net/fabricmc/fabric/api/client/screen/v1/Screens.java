package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.gui.screens.Screen;

/**
 * Surface Irium — fabric-screen-api-v1 Screens. Le vrai impl passe par
 * ScreenExtensions (mixin) ; ici on lit directement les enfants du screen :
 * getWidgets retourne les AbstractWidget de la children list (ce que
 * ModMenu fait pour insérer son bouton "Mods" dans le TitleScreen).
 */
public final class Screens {

    private Screens() {}

    @SuppressWarnings("unchecked")
    public static java.util.List<net.minecraft.client.gui.components.AbstractWidget> getWidgets(Screen screen) {
        java.util.Objects.requireNonNull(screen, "Screen cannot be null");
        java.util.List<net.minecraft.client.gui.components.AbstractWidget> out = new java.util.ArrayList<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) out.add(w);
        }
        return out;
    }

    public static net.minecraft.client.gui.Font getFont(Screen screen) {
        java.util.Objects.requireNonNull(screen, "Screen cannot be null");
        return screen.getFont();
    }

    public static net.minecraft.client.Minecraft getMinecraft(Screen screen) {
        java.util.Objects.requireNonNull(screen, "Screen cannot be null");
        return net.minecraft.client.Minecraft.getInstance();
    }
}
