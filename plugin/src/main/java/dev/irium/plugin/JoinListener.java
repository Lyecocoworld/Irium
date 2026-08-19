package dev.irium.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Propose le consentement une seule fois par joueur, 40 ticks après le join. */
final class JoinListener implements Listener {

    private final IriumPlugin plugin;

    JoinListener(IriumPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        if (plugin.hasChosen(p.getUniqueId())) {
            // déjà choisi : silencieux (règle : jamais redemandé)
            if (plugin.isActive(p.getUniqueId())) {
                p.getScheduler().runDelayed(plugin, null, () -> plugin.sendHello(p), 40L);
            }
            return;
        }
        p.getScheduler().runDelayed(plugin, null, () -> ConsentFlow.offer(plugin, p), 40L);
    }
}
