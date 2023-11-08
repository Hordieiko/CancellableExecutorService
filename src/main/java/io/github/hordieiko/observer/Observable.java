package io.github.hordieiko.observer;

/**
 * Represents an observable entity.
 *
 * @param <T> the event type
 * @param <L> the listener type
 */
public interface Observable<T, L> {
    /**
     * Subscribes the listener for the specified event.
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    void subscribe(final T event, final L listener);

    /**
     * Unsubscribes the listener for the specified event.
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    void unsubscribe(final T event, final L listener);
}
