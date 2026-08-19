package dev.irium.plugin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Le dialog de consentement Irium — confirmation oui/non, native client.
 * Gate : protocol >= 767 (dialogs). Sinon fallback chat cliquable.
 * Signatures vérifiées par javap sur paper-api 26.1.2.build.74-stable.
 */
final class ConsentFlow {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ConsentFlow() {}

    static void offer(IriumPlugin plugin, Player p) {
        if (plugin.isActive(p.getUniqueId())) {
            p.sendMessage(plugin.msg("prefix").append(plugin.msg("already_active")));
            return;
        }
        if (isDialogCapable(p)) {
            try {
                showDialog(plugin, p);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("dialog échoué, fallback chat: " + t);
            }
        }
        chatFallback(plugin, p);
    }

    /** Gate CLIENT (protocol), jamais la version serveur. Canvas peut ne pas exposer → supposer capable. */
    private static boolean isDialogCapable(Player p) {
        try {
            return p.getProtocolVersion() >= 767;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void showDialog(IriumPlugin plugin, Player p) {
        // Callbacks : uses(1) = anti-replay natif ; reschedule Folia-safe vers l'EntityScheduler
        DialogAction.CustomClickAction yesAction = DialogAction.customClick(
                (response, audience) -> {
                    if (audience instanceof Player player) {
                        player.getScheduler().run(plugin, null, () -> accept(plugin, player));
                    }
                },
                ClickCallback.Options.builder().uses(1).build());

        DialogAction.CustomClickAction noAction = DialogAction.customClick(
                (response, audience) -> {
                    if (audience instanceof Player player) {
                        player.getScheduler().run(plugin, null, () -> decline(plugin, player));
                    }
                },
                ClickCallback.Options.builder().uses(1).build());

        ActionButton yes = ActionButton.builder(label(plugin.raw("dialog.yes"), "#55FFA4"))
                .action(yesAction)
                .tooltip(mm(plugin.raw("dialog.yes_tooltip")))
                .build();
        ActionButton no = ActionButton.builder(label(plugin.raw("dialog.no"), "#B8BBC2"))
                .action(noAction)
                .tooltip(mm(plugin.raw("dialog.no_tooltip")))
                .build();

        DialogBase base = DialogBase.builder(boldTitle(plugin.raw("dialog.title")))
                .body(java.util.List.of(
                        DialogBody.plainMessage(mm(plugin.raw("dialog.body"))),
                        DialogBody.plainMessage(mm(plugin.raw("dialog.body2"))),
                        DialogBody.plainMessage(mm(plugin.raw("dialog.body3")))))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        Dialog dialog = Dialog.create(f -> f.empty().base(base)
                .type(DialogType.confirmation(yes, no)));

        p.showDialog(dialog);
    }

    private static void chatFallback(IriumPlugin plugin, Player p) {
        Component yes = MM.deserialize(plugin.raw("chat_fallback.click_yes"))
                .clickEvent(ClickEvent.runCommand("/irium __accept"))
                .hoverEvent(HoverEvent.showText(mm(plugin.raw("chat_fallback.hover_yes"))));
        Component no = MM.deserialize(plugin.raw("chat_fallback.click_no"))
                .clickEvent(ClickEvent.runCommand("/irium __decline"))
                .hoverEvent(HoverEvent.showText(mm(plugin.raw("chat_fallback.hover_no"))));
        p.sendMessage(plugin.msg("prefix").append(plugin.msg("chat_fallback.prompt")));
        p.sendMessage(yes.append(Component.text("  ")).append(no));
    }

    static void accept(IriumPlugin plugin, Player p) {
        plugin.setActive(p.getUniqueId(), true);
        p.sendMessage(plugin.msg("prefix").append(plugin.msg("welcome_active")));
        plugin.sendHello(p); // J2 : l'agent répondra et le handshake complet se fera
    }

    static void decline(IriumPlugin plugin, Player p) {
        plugin.setActive(p.getUniqueId(), false);
        p.sendMessage(plugin.msg("prefix").append(plugin.msg("welcome_classic")));
    }

    /* ------------ helpers charte ------------ */

    private static Component mm(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Bouton : couleur uniquement, pas d'emoji, pas d'italique. */
    private static Component label(String text, String hex) {
        return MM.deserialize("<" + hex + ">" + text + "</" + hex + ">")
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Titre : BOLD uniquement, SANS couleur (charte CocoWorld). */
    private static Component boldTitle(String s) {
        return MM.deserialize("<bold>" + s + "</bold>").decoration(TextDecoration.ITALIC, false);
    }
}
