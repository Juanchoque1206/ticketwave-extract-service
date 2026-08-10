package com.ticketwave.infrastructure.controller;

import com.ticketwave.domain.bus.EventBus;
import com.ticketwave.domain.events.TicketOrderCreated;
import com.ticketwave.infrastructure.dto.event.PublishTicketOrderCreatedRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Development/testing endpoint that publishes a TicketOrderCreated domain event
 * through the EventBus. Under the {@code rabbitmq} profile the event is routed
 * on the ticketwave.events exchange (RabbitMQEventBusAdapter) so the saga
 * orchestrator and any subscriber can be exercised from Swagger.
 */
@RestController
@RequestMapping("/api/events")
@Tag(name = "Event Publishing", description = "Manual event publishing for local/rabbitmq testing")
public class EventPublishController {

    private final EventBus eventBus;

    public EventPublishController(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Operation(summary = "Publish TicketOrderCreated to the event bus",
            description = "Builds a TicketOrderCreated event from the payload and publishes it through the " +
                    "configured EventBus. On the rabbitmq profile the event is sent to the ticketwave.events exchange.")
    @PostMapping("/publish/ticket-order-created")
    public ResponseEntity<UUID> publishTicketOrderCreated(
            @Valid @RequestBody PublishTicketOrderCreatedRequest request) {
        TicketOrderCreated event = new TicketOrderCreated(
                UUID.randomUUID(),
                Instant.now(),
                request.orderId(),
                request.userId(),
                request.eventId(),
                request.quantity(),
                request.total(),
                request.discount() != null ? request.discount() : java.math.BigDecimal.ZERO);
        System.out.println("sdfjk==========>>>" + event);
        eventBus.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(event.id());
    }
}
