package dev.irium.agent.mixin;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;

/** Logger Mixin → stdout préfixé [irium:mixin]. */
public final class MixinLogger implements ILogger {

    private final String id;

    MixinLogger(String id) { this.id = id; }

    @Override public String getId() { return id; }
    @Override public String getType() { return "Irium Stdout"; }

    private void write(Level level, String message, Object... args) {
        String msg = message;
        if (args != null && args.length > 0) {
            StringBuilder sb = new StringBuilder(message);
            for (Object a : args) sb.append(' ').append(a);
            msg = sb.toString();
        }
        System.out.println("[irium:mixin:" + id + "/" + level.name() + "] " + msg);
    }

    @Override public void catching(Level level, Throwable t) { write(level, "catching", t); }
    @Override public void catching(Throwable t) { write(Level.ERROR, "catching", t); }
    @Override public void debug(String m, Object... a) { }
    @Override public void debug(String m, Throwable t) { }
    @Override public void error(String m, Object... a) { write(Level.ERROR, m, a); }
    @Override public void error(String m, Throwable t) { write(Level.ERROR, m, t); }
    @Override public void fatal(String m, Object... a) { write(Level.ERROR, m, a); }
    @Override public void fatal(String m, Throwable t) { write(Level.ERROR, m, t); }
    @Override public void info(String m, Object... a) { write(Level.INFO, m, a); }
    @Override public void info(String m, Throwable t) { write(Level.INFO, m, t); }
    @Override public void log(Level l, String m, Object... a) { write(l, m, a); }
    @Override public void log(Level l, String m, Throwable t) { write(l, m, t); }
    @Override public <T extends Throwable> T throwing(T t) { return t; }
    @Override public void trace(String m, Object... a) { }
    @Override public void trace(String m, Throwable t) { }
    @Override public void warn(String m, Object... a) { write(Level.WARN, m, a); }
    @Override public void warn(String m, Throwable t) { write(Level.WARN, m, t); }
}
