package com.ticketwave.ticketorder.infrastructure.bus;

import com.ticketwave.ticketorder.domain.bus.EventBus;
import com.ticketwave.ticketorder.domain.bus.EventHandler;
import com.ticketwave.ticketorder.domain.events.DomainEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory EventBus for local development, active when no RabbitMQ broker is
 * configured (the local profile). Published events are dispatched synchronously
 * to all registered subscribers within the process and recorded for assertion.
 */
public class InMemoryEventBus implements EventBus {

    private final Map<Class<?>, List<Consumer<DomainEvent>>> handlers = new ConcurrentHashMap<>();
    private final List<DomainEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
        for (Consumer<DomainEvent> consumer : handlers.getOrDefault(event.getClass(), List.of())) {
            consumer.accept(event);
        }
    }

    @Override
    public <E extends DomainEvent> void subscribe(Class<E> eventType, EventHandler<E> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(event -> handler.handle(eventType.cast(event)));
    }

    public List<DomainEvent> published() {
        return published;
    }

    public void clear() {
        published.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<T> published(Class<T> type) {
        return published.stream().filter(type::isInstance).map(e -> (T) e).toList();
    }
}
