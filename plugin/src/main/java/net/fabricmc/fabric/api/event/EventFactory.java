package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

/**
 * Adaptateur Irium — EventFactory officielle : createArrayBacked avec invoker
 * combinant les handlers dans l'ordre d'enregistrement ; tableau vide → la
 * factory produit un no-op (contrat officiel).
 */
public final class EventFactory {

    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, invokerFactory);
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, invokerFactory);
    }

    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory,
                                                 Identifier... phases) {
        return new ArrayBackedEvent<>(type, invokerFactory);
    }

    public static String getHandlerName(Object handler) {
        return handler.getClass().getName();
    }

    public static boolean isProfilingEnabled() {
        return false;
    }

    public static void invalidate() {}

    private static final class ArrayBackedEvent<T> extends Event<T> {
        private final Class<? super T> type;
        private final Function<T[], T> invokerFactory;
        private final List<T> handlers = new ArrayList<>();

        ArrayBackedEvent(Class<? super T> type, Function<T[], T> invokerFactory) {
            this.type = type;
            this.invokerFactory = invokerFactory;
            update();
        }

        @SuppressWarnings("unchecked")
        private synchronized void update() {
            T[] array = handlers.toArray((T[]) Array.newInstance(type, handlers.size()));
            invoker = invokerFactory.apply(array);
        }

        @Override
        public synchronized void register(T handler) {
            if (handler == null) return;
            handlers.add(handler);
            update();
        }
    }
}
