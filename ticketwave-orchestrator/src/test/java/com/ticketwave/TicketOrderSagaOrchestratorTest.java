package com.ticketwave;

import com.ticketwave.domain.bus.CommandBus;
import com.ticketwave.domain.bus.EventBus;
import com.ticketwave.domain.commands.CancelTicketOrderCommand;
import com.ticketwave.domain.commands.IssueTicketCommand;
import com.ticketwave.domain.commands.NotifyOrderCommand;
import com.ticketwave.domain.commands.ProcessPaymentCommand;
import com.ticketwave.domain.commands.RefundPaymentCommand;
import com.ticketwave.domain.events.NotificationSent;
import com.ticketwave.domain.events.PaymentAuthorized;
import com.ticketwave.domain.events.PaymentFailed;
import com.ticketwave.domain.events.TicketDeliveryFailed;
import com.ticketwave.domain.events.TicketIssued;
import com.ticketwave.domain.events.TicketOrderCompleted;
import com.ticketwave.domain.events.TicketOrderCreated;
import com.ticketwave.domain.events.TicketRefunded;
import com.ticketwave.domain.saga.SagaState;
import com.ticketwave.domain.saga.SagaStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the saga orchestrator through the in-memory bus: every domain event
 * published on the event bus must advance the saga snapshot and result in the
 * expected command on the command bus.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketOrderSagaOrchestratorTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private CommandBus commandBus;
    @Autowired
    private SagaStateRepository sagaRepository;
    @Autowired
    private com.ticketwave.domain.saga.TicketOrderSagaOrchestrator orchestrator;

    private final List<Object> sentCommands = new ArrayList<>();

    @BeforeEach
    void captureCommands() {
        sentCommands.clear();
        commandBus.subscribe(ProcessPaymentCommand.class, sentCommands::add);
        commandBus.subscribe(IssueTicketCommand.class, sentCommands::add);
        commandBus.subscribe(NotifyOrderCommand.class, sentCommands::add);
        commandBus.subscribe(CancelTicketOrderCommand.class, sentCommands::add);
        commandBus.subscribe(RefundPaymentCommand.class, sentCommands::add);
    }

    private TicketOrderCreated orderCreated(UUID orderId, UUID userId, UUID eventId) {
        return new TicketOrderCreated(UUID.randomUUID(), Instant.now(),
                orderId, userId, eventId, 2, new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    private PaymentAuthorized paymentAuthorized(UUID orderId) {
        return new PaymentAuthorized(UUID.randomUUID(), Instant.now(),
                orderId, UUID.randomUUID(), new BigDecimal("100.00"), "TXN-1");
    }

    private PaymentFailed paymentFailed(UUID orderId) {
        return new PaymentFailed(UUID.randomUUID(), Instant.now(),
                orderId, UUID.randomUUID(), new BigDecimal("100.00"), "provider declined");
    }

    private TicketIssued ticketIssued(UUID orderId) {
        return new TicketIssued(UUID.randomUUID(), Instant.now(),
                orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()));
    }

    private TicketDeliveryFailed ticketDeliveryFailed(UUID orderId) {
        return new TicketDeliveryFailed(UUID.randomUUID(), Instant.now(),
                orderId, UUID.randomUUID(), "issuance failed");
    }

    private NotificationSent notificationSent(UUID orderId) {
        return new NotificationSent(UUID.randomUUID(), Instant.now(),
                orderId, UUID.randomUUID(), UUID.randomUUID());
    }

    private TicketRefunded ticketRefunded(UUID orderId) {
        return new TicketRefunded(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.00"));
    }

    @Test
    void orderCreated_startsSagaAndRequestsPayment() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(1, sentCommands.size());
        ProcessPaymentCommand payment = (ProcessPaymentCommand) sentCommands.get(0);
        assertEquals(orderId, payment.orderId());
        assertEquals(new BigDecimal("100.00"), payment.amount());
    }

    @Test
    void duplicateOrderCreated_isIgnored() {
        UUID orderId = UUID.randomUUID();
        TicketOrderCreated created = orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID());
        eventBus.publish(created);
        eventBus.publish(created);

        assertEquals(1, sentCommands.size());
    }

    @Test
    void paymentAuthorized_advancesToTicketIssuance() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        sentCommands.clear();

        eventBus.publish(paymentAuthorized(orderId));

        assertEquals(1, sentCommands.size());
        IssueTicketCommand issue = (IssueTicketCommand) sentCommands.get(0);
        assertEquals(orderId, issue.orderId());
        assertEquals(2, issue.quantity());
    }

    @Test
    void paymentFailed_requestsOrderCancellation() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        sentCommands.clear();

        eventBus.publish(paymentFailed(orderId));

        assertEquals(1, sentCommands.size());
        CancelTicketOrderCommand cancel = (CancelTicketOrderCommand) sentCommands.get(0);
        assertEquals(orderId, cancel.orderId());
        assertEquals("provider declined", cancel.reason());
    }

    @Test
    void ticketIssued_advancesToNotification() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        eventBus.publish(paymentAuthorized(orderId));
        sentCommands.clear();

        eventBus.publish(ticketIssued(orderId));

        assertEquals(1, sentCommands.size());
        NotifyOrderCommand notify = (NotifyOrderCommand) sentCommands.get(0);
        assertEquals(orderId, notify.orderId());
    }

    @Test
    void ticketDeliveryFailed_requestsRefund() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        sentCommands.clear();

        eventBus.publish(ticketDeliveryFailed(orderId));

        assertEquals(1, sentCommands.size());
        RefundPaymentCommand refund = (RefundPaymentCommand) sentCommands.get(0);
        assertEquals(orderId, refund.orderId());
        assertEquals(new BigDecimal("100.00"), refund.amount());
    }

    @Test
    void notificationSent_completesSaga() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        eventBus.publish(paymentAuthorized(orderId));
        eventBus.publish(ticketIssued(orderId));

        List<TicketOrderCompleted> completed = new ArrayList<>();
        eventBus.subscribe(TicketOrderCompleted.class, completed::add);

        eventBus.publish(notificationSent(orderId));

        assertEquals(1, completed.size());
        assertEquals(orderId, completed.get(0).orderId());
        SagaState state = sagaRepository.findByOrderId(orderId).orElse(null);
        assertNotNull(state, "completed saga should be persisted");
        assertTrue(state.status().name().equals("COMPLETED"));
    }

    @Test
    void refunded_marksCompensatedSagaAsDone() {
        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        eventBus.publish(ticketDeliveryFailed(orderId));

        eventBus.publish(ticketRefunded(orderId));

        SagaState state = sagaRepository.findByOrderId(orderId).orElse(null);
        assertNotNull(state, "compensated saga should be persisted");
        assertEquals("COMPENSATED", state.status().name());
    }

    @Test
    void recover_resendsPendingSteps() {
        sagaRepository.findAll().forEach(state -> sagaRepository.deleteById(state.sagaId()));
        sentCommands.clear();

        UUID orderId = UUID.randomUUID();
        eventBus.publish(orderCreated(orderId, UUID.randomUUID(), UUID.randomUUID()));
        sentCommands.clear();

        orchestrator.recover();

        assertEquals(1, sentCommands.size(), "recover() must re-drive the pending payment step");
        assertTrue(sentCommands.get(0) instanceof ProcessPaymentCommand);
    }
}
