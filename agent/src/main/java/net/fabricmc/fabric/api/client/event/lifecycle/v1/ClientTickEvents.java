package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Surface Irium de net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 * (forme officielle fabric-lifecycle-events-v1, 26.2).
 * Les hooks sont portés par la recette tick sur Minecraft.tick()V (ancre canonique).
 */
public final class ClientTickEvents {

    @FunctionalInterface
    public interface StartTick { void onStartTick(net.minecraft.client.Minecraft client); }

    @FunctionalInterface
    public interface EndTick { void onEndTick(net.minecraft.client.Minecraft client); }

    @FunctionalInterface
    public interface StartLevelTick {
        void onStartLevelTick(net.minecraft.client.Minecraft client, net.minecraft.client.multiplayer.ClientLevel level);
    }

    @FunctionalInterface
    public interface EndLevelTick {
        void onEndLevelTick(net.minecraft.client.Minecraft client, net.minecraft.client.multiplayer.ClientLevel level);
    }

    public static final Event<StartTick> START_CLIENT_TICK =
            EventFactory.createArrayBacked(StartTick.class,
                    listeners -> client -> { for (StartTick l : listeners) l.onStartTick(client); });

    public static final Event<EndTick> END_CLIENT_TICK =
            EventFactory.createArrayBacked(EndTick.class,
                    listeners -> client -> { for (EndTick l : listeners) l.onEndTick(client); });

    public static final Event<StartLevelTick> START_LEVEL_TICK =
            EventFactory.createArrayBacked(StartLevelTick.class,
                    listeners -> (client, level) -> { for (StartLevelTick l : listeners) l.onStartLevelTick(client, level); });

    public static final Event<EndLevelTick> END_LEVEL_TICK =
            EventFactory.createArrayBacked(EndLevelTick.class,
                    listeners -> (client, level) -> { for (EndLevelTick l : listeners) l.onEndLevelTick(client, level); });

    private ClientTickEvents() {}
}
