package com.ticketwave.ticketorder.infrastructure.controller;

import com.ticketwave.ticketorder.domain.commands.CancelTicketOrderCommand;
import com.ticketwave.ticketorder.domain.commands.IssueTicketCommand;
import com.ticketwave.ticketorder.domain.commands.ProcessPaymentCommand;
import com.ticketwave.ticketorder.domain.commands.RefundPaymentCommand;
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
        Map<String, Object> payload = switch (type.toLowerCase()) {
            case "payment-authorized" -> paymentAuthorized();
            case "payment-failed" -> paymentFailed();
            case "ticket-order-created" -> ticketOrderCreated();
            case "ticket-issued" -> ticketIssued();
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        };
        publishTo(RabbitMQEventBusAdapter.EXCHANGE, type, payload);
        return ResponseEntity.accepted().body(Map.of(
                "exchange", RabbitMQEventBusAdapter.EXCHANGE,
                "routingKey", type,
                "payload", payload));
    }

    @PostMapping("/command/{type}")
    public ResponseEntity<Map<String, Object>> publishCommand(@PathVariable String type) {
        Map<String, Object> payload = switch (type.toLowerCase()) {
            case "cancel-ticket-order" -> cancelTicketOrder();
            case "process-payment" -> processPayment();
            case "issue-ticket" -> issueTicket();
            case "refund-payment" -> refundPayment();
            default -> throw new IllegalArgumentException("Unknown command type: " + type);
        };
        publishTo(RabbitMQCommandBusAdapter.EXCHANGE, type, payload);
        return ResponseEntity.accepted().body(Map.of(
                "exchange", RabbitMQCommandBusAdapter.EXCHANGE,
                "routingKey", type,
                "payload", payload));
    }

    private void publishTo(String exchange, String routingKey, Object message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    private Map<String, Object> paymentAuthorized() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.events.PaymentAuthorized",
                "id", UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "total", BigDecimal.valueOf(150.00),
                "providerTransactionId", "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private Map<String, Object> paymentFailed() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.events.PaymentFailed",
                "id", UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "total", BigDecimal.valueOf(150.00),
                "reason", "Insufficient funds");
    }

    private Map<String, Object> ticketOrderCreated() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.events.TicketOrderCreated",
                "id", UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "eventId", UUID.randomUUID().toString(),
                "quantity", 2,
                "total", BigDecimal.valueOf(300.00),
                "discount", BigDecimal.ZERO);
    }

    private Map<String, Object> ticketIssued() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.events.TicketIssued",
                "id", UUID.randomUUID().toString(),
                "occurredAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "eventId", UUID.randomUUID().toString(),
                "ticketIds", List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    private Map<String, Object> cancelTicketOrder() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.commands.CancelTicketOrderCommand",
                "commandId", UUID.randomUUID().toString(),
                "issuedAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "eventId", UUID.randomUUID().toString(),
                "quantity", 2,
                "reason", "User cancellation");
    }

    private Map<String, Object> processPayment() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.commands.ProcessPaymentCommand",
                "commandId", UUID.randomUUID().toString(),
                "issuedAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "provider", "STRIPE",
                "amount", BigDecimal.valueOf(150.00));
    }

    private Map<String, Object> issueTicket() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.commands.IssueTicketCommand",
                "commandId", UUID.randomUUID().toString(),
                "issuedAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "eventId", UUID.randomUUID().toString(),
                "quantity", 2);
    }

    private Map<String, Object> refundPayment() {
        return Map.of(
                "@class", "com.ticketwave.ticketorder.domain.commands.RefundPaymentCommand",
                "commandId", UUID.randomUUID().toString(),
                "issuedAt", Instant.now().toString(),
                "orderId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "amount", BigDecimal.valueOf(150.00),
                "reason", "Order cancelled");
    }
}
