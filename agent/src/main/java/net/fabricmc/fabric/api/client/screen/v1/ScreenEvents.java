package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Surface Irium — fabric-screen-api-v1 (ModMenu etc.).
 * BEFORE_INIT/AFTER_INIT : involués à chaque Screen.init via notre
 * ScreenInitHook (recipe-transformée une fois, fire sur les events).
 */
public final class ScreenEvents {

    public interface BeforeInit {
        void beforeInit(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight);
    }

    public interface AfterInit {
        void afterInit(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight);
    }

    public interface Remove {
        void onRemove(Screen screen);
    }

    public interface BeforeExtract { }

    public interface AfterExtract { }

    public interface AfterBackground {
        default void afterRenderBackground(Screen screen, int mouseX, int mouseY) {}
    }

    public static final Event<BeforeInit> BEFORE_INIT =
            EventFactory.createArrayBacked(BeforeInit.class,
                    listeners -> (mc, s, w, h) -> {
                        for (BeforeInit l : listeners) l.beforeInit(mc, s, w, h);
                    });

    public static final Event<AfterInit> AFTER_INIT =
            EventFactory.createArrayBacked(AfterInit.class,
                    listeners -> (mc, s, w, h) -> {
                        for (AfterInit l : listeners) l.afterInit(mc, s, w, h);
                    });

    private ScreenEvents() {}

    /** Invoqué par le hook d'init (ScreenInitHook). */
    public static void fire(Minecraft mc, Screen screen, int w, int h) {
        BEFORE_INIT.invoker().beforeInit(mc, screen, w, h);
        AFTER_INIT.invoker().afterInit(mc, screen, w, h);
    }
}
