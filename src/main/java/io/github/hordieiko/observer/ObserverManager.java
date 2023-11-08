package io.github.hordieiko.observer;

import java.util.function.Consumer;

/**
 * The {@code ObserverManager} manages events to notify subscribed listeners about changes.
 *
 * @param <T> the event type
 * @param <V> the data type that listeners get to work on
 * @param <L> the listener type
 */
public interface ObserverManager<T, V, L extends Consumer<V>> extends Observable<T, L> {

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    void subscribe(final T event, final L listener);

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    void unsubscribe(final T event, final L listener);

    /**
     * Notifies subscribed listeners about changes.
     *
     * @param event the event
     * @param data  the data that listeners get to work on
     */
    void notify(final T event, final V data);
}
