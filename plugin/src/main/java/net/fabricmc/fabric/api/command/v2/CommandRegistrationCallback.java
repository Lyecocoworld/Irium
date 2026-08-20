package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Adaptateur Irium — Brigadier live depuis le plugin Paper. */
@FunctionalInterface
public interface CommandRegistrationCallback {

    Event<CommandRegistrationCallback> EVENT =
            EventFactory.createArrayBacked(CommandRegistrationCallback.class,
                    ls -> (dispatcher, buildContext, environment) -> {
                        for (CommandRegistrationCallback l : ls)
                            l.register(dispatcher, buildContext, environment);
                    });

    void register(CommandDispatcher<CommandSourceStack> dispatcher,
                  CommandBuildContext buildContext,
                  Commands.CommandSelection environment);
}
