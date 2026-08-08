package com.ticketwave.ticketorder.domain.bus;

import com.ticketwave.ticketorder.domain.events.DomainEvent;

/**
 * Hexagonal port for asynchronous, decoupled event delivery.
 * Domain and application layers only depend on this interface; the concrete
 * transport (in-memory, RabbitMQ, ...) is chosen in the infrastructure layer.
 */
public interface EventBus {

    void publish(DomainEvent event);

    <E extends DomainEvent> void subscribe(Class<E> eventType, EventHandler<E> handler);
}
