package com.ticketwave.ticketorder.domain.bus;

import com.ticketwave.ticketorder.domain.events.DomainEvent;

/**
 * Functional handler contract used to register consumers of a domain event type.
 */
@FunctionalInterface
public interface EventHandler<E extends DomainEvent> {

    void handle(E event);
}
