package io.github.hordieiko.observer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The {@code EventManager} manages events to notify subscribed listeners about changes.
 *
 * @param <T> the event type
 * @param <V> the data type that listeners get to work on
 * @param <L> the listener type
 */
public final class EventManager<T, V, L extends Consumer<V>> implements ObserverManager<T, V, L> {
    private final Map<T, Collection<L>> listenersMap = new ConcurrentHashMap<>();

    private final Supplier<Collection<L>> listenersCollectionSupplier;

    /**
     * Constructs a new {@link EventManager}
     * with default concurrent {@link ConcurrentLinkedQueue collection} for listeners.
     */
    public EventManager() {
        this(ConcurrentLinkedQueue::new);
    }

    /**
     * Constructs a new {@link EventManager} that will use the given collection for listeners.
     *
     * <p>Pay attention that the collection of listeners may be updated concurrently.
     *
     * @param listenersCollectionSupplier a function which returns a new, mutable collection for listeners
     * @throws NullPointerException if the {@code listenersCollectionSupplier} is null
     */
    public EventManager(final Supplier<Collection<L>> listenersCollectionSupplier) {
        if (listenersCollectionSupplier == null) throw new NullPointerException();
        this.listenersCollectionSupplier = listenersCollectionSupplier;
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void subscribe(final T event, final L listener) {
        listenersMap.computeIfAbsent(event, _ -> listenersCollectionSupplier.get()).add(listener);
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void unsubscribe(final T event, final L listener) {
        final var listeners = listenersMap.get(event);
        if (listeners != null) listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     *
     * @param event the event
     * @param data  the data that listeners get to work on
     */
    @Override
    public void notify(final T event, final V data) {
        final var listeners = listenersMap.get(event);
        if (listeners != null && !listeners.isEmpty())
            listeners.forEach(listener -> listener.accept(data));
    }
}
