package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Surface Irium côté CLIENT : les mods universels (SVC) enregistrent leurs
 * commandes serveur via CommandRegistrationCallback. Sur un client, l'event
 * ne se déclenche jamais (commandes enregistrées côté serveur Irium).
 *
 * Note forme : la signature utilise Commands.CommandSelection comme l'API
 * officielle — le crash jspecify de compilation vient d'une collision de
 * classpath, réglé par ordre de dépendances.
 */
@FunctionalInterface
public interface CommandRegistrationCallback {

    void register(CommandDispatcher<CommandSourceStack> dispatcher,
                  CommandBuildContext registryAccess,
                  Commands.CommandSelection environment);

    Event<CommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(CommandRegistrationCallback.class);
}
