package com.ticketwave.ticketorder.domain.events;

import java.time.Instant;
import java.util.UUID;

public record FraudDetected(
        UUID id,
        Instant occurredAt,
        UUID userId,
        String ipAddress,
        String reason) implements DomainEvent {
}
