package dev.irium.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * M2 : handshake à CHAQUE join (la classification précède tout le reste),
 * consentement seulement pour les joueurs sans choix, oubli au quit.
 */
final class JoinListener implements Listener {

    private final IriumPlugin plugin;

    JoinListener(IriumPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        // 1. Classification systématique (vanilla vs agent) — 40 ticks après le join.
        //    Canvas 26.2 : runDelayed(plugin, Consumer<ScheduledTask> task, Runnable retired, delay)
        //    -> le travail va dans le Consumer ; le Runnable n'est QUE le callback retired.
        p.getScheduler().runDelayed(plugin, task -> plugin.sendHello(p), () -> {
        }, 40L);
        // 2. Consentement seulement si jamais choisi.
        if (!plugin.hasChosen(p.getUniqueId())) {
            p.getScheduler().runDelayed(plugin, task -> ConsentFlow.offer(plugin, p), () -> {
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.handshake().forget(e.getPlayer().getUniqueId());
    }
}
