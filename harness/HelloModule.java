import dev.irium.agent.module.IriumContext;
import dev.irium.agent.module.IriumModule;

/**
 * M4 lab module — c'est LE test : ces octets seront poussés par le serveur
 * sur le canal irium:module, définis dans un classloader dédié du client,
 * et exécutés là-bas. Le client ne contenait aucune trace de ce code.
 */
public class HelloModule implements IriumModule {

    @Override
    public void onEnable(IriumContext ctx) {
        ctx.log("HelloModule ACTIF — code reçu par le réseau, exécuté dans ce client");
        ctx.emit("hello", "module exécuté côté client (classe=" + getClass().getName()
                + ", cl=" + getClass().getClassLoader().getClass().getSimpleName() + ")");
    }

    @Override
    public void onDisable() {
        System.err.println("[irium][mod] HelloModule désactivé — session terminée");
    }
}
