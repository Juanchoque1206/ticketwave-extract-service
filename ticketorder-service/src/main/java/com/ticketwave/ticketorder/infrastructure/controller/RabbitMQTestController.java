package com.ticketwave.ticketorder.infrastructure.controller;

import com.ticketwave.ticketorder.domain.commands.CancelTicketOrderCommand;
import com.ticketwave.ticketorder.domain.commands.Command;
import com.ticketwave.ticketorder.domain.commands.IssueTicketCommand;
import com.ticketwave.ticketorder.domain.commands.ProcessPaymentCommand;
import com.ticketwave.ticketorder.domain.commands.RefundPaymentCommand;
import com.ticketwave.ticketorder.domain.events.DomainEvent;
import com.ticketwave.ticketorder.domain.events.PaymentAuthorized;
import com.ticketwave.ticketorder.domain.events.PaymentFailed;
import com.ticketwave.ticketorder.domain.events.TicketIssued;
import com.ticketwave.ticketorder.domain.events.TicketOrderCreated;
import com.ticketwave.ticketorder.infrastructure.bus.RabbitMQCommandBusAdapter;
import com.ticketwave.ticketorder.infrastructure.bus.RabbitMQEventBusAdapter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only controller to publish sample events and commands to the shared
 * RabbitMQ exchanges ({@code ticketwave.events} / {@code ticketwave.commands}).
 * Useful to exercise the saga against a running broker without a full purchase
 * flow. Active whenever the rabbitmq profile is enabled.
 */
@RestController
@RequestMapping("/api/test/rabbitmq")
@Tag(name = "RabbitMQ Test", description = "Publish sample events/commands to the shared bus")
public class RabbitMQTestController {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQTestController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/event/{type}")
    public ResponseEntity<Map<String, Object>> publishEvent(@PathVariable String type) {
        DomainEvent event = switch (type.toLowerCase()) {
            case "payment-authorized" -> new PaymentAuthorized(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(150.00), "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            case "payment-failed" -> new PaymentFailed(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(150.00), "Insufficient funds");
            case "ticket-order-created" -> new TicketOrderCreated(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), 2, BigDecimal.valueOf(300.00), BigDecimal.ZERO);
            case "ticket-issued" -> new TicketIssued(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()));
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        };
        publishTo(RabbitMQEventBusAdapter.EXCHANGE, type, event);
        return ResponseEntity.accepted().body(Map.of(
                "exchange", RabbitMQEventBusAdapter.EXCHANGE,
                "routingKey", type,
                "payload", event));
    }

    @PostMapping("/command/{type}")
    public ResponseEntity<Map<String, Object>> publishCommand(@PathVariable String type) {
        Command command = switch (type.toLowerCase()) {
            case "cancel-ticket-order" -> new CancelTicketOrderCommand(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), 2, "User cancellation");
            case "process-payment" -> new ProcessPaymentCommand(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "STRIPE", BigDecimal.valueOf(150.00));
            case "issue-ticket" -> new IssueTicketCommand(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), 2);
            case "refund-payment" -> new RefundPaymentCommand(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                    BigDecimal.valueOf(150.00), "Order cancelled");
            default -> throw new IllegalArgumentException("Unknown command type: " + type);
        };
        publishTo(RabbitMQCommandBusAdapter.EXCHANGE, type, command);
        return ResponseEntity.accepted().body(Map.of(
                "exchange", RabbitMQCommandBusAdapter.EXCHANGE,
                "routingKey", type,
                "payload", command));
    }

    private void publishTo(String exchange, String routingKey, Object message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
