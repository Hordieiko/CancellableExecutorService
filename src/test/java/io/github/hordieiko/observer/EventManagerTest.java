package io.github.hordieiko.observer;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventManagerTest {

    @Test
    void testEventManager() {
        final var eventName = "eventName";
        final var expectedValue1 = "expected value 1";
        final var expectedValue2 = "expected value 2";
        final var listener1 = new TestListener();
        final var listener2 = new TestListener();
        final var eventManager = new EventManager<String, String, Consumer<String>>();

        eventManager.subscribe(eventName, listener1);
        eventManager.subscribe(eventName, listener2);

        eventManager.notify(eventName, expectedValue1);

        assertEquals(expectedValue1, listener1.value);
        assertEquals(expectedValue1, listener2.value);

        eventManager.unsubscribe(eventName, listener2);

        eventManager.notify(eventName, expectedValue2);

        assertEquals(expectedValue2, listener1.value);
        assertEquals(expectedValue1, listener2.value);
    }

    static class TestListener implements Consumer<String> {
        String value;

        @Override
        public void accept(final String string) {
            this.value = string;
        }
    }
}