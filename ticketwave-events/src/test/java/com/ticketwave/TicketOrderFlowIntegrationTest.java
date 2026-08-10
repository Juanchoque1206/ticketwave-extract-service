package com.ticketwave;

import com.ticketwave.domain.bus.CommandBus;
import com.ticketwave.domain.event.Event;
import com.ticketwave.domain.event.EventRepository;
import com.ticketwave.domain.event.EventStatus;
import com.ticketwave.domain.commands.IssueTicketCommand;
import com.ticketwave.domain.commands.NotifyOrderCommand;
import com.ticketwave.domain.commands.ProcessPaymentCommand;
import com.ticketwave.domain.notification.NotificationRepository;
import com.ticketwave.domain.payment.Payment;
import com.ticketwave.domain.payment.PaymentRepository;
import com.ticketwave.domain.payment.PaymentStatus;
import com.ticketwave.domain.ticket.Ticket;
import com.ticketwave.domain.ticket.TicketRepository;
import com.ticketwave.domain.user.AppUser;
import com.ticketwave.domain.user.Role;
import com.ticketwave.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the command-handling half of the purchase saga offline. The standalone
 * ticketwave-orchestrator turns a TicketOrderCreated event into the
 * ProcessPaymentCommand, IssueTicketCommand and NotifyOrderCommand that this
 * service consumes; this test replays those commands through the in-memory
 * command bus and asserts the payment, tickets and notification side effects.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketOrderFlowIntegrationTest {

    @Autowired
    private CommandBus commandBus;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void orderCreatedEvent_drivesSagaEndToEnd() {
        AppUser user = createUser();
        Event event = createEvent();
        UUID orderId = UUID.randomUUID();
        BigDecimal total = event.getBasePrice().multiply(BigDecimal.valueOf(2));
        int quantity = 2;

        commandBus.send(new ProcessPaymentCommand(UUID.randomUUID(), Instant.now(),
                orderId, "STRIPE", total));
        commandBus.send(new IssueTicketCommand(UUID.randomUUID(), Instant.now(),
                orderId, user.getId(), event.getId(), quantity));
        commandBus.send(new NotifyOrderCommand(UUID.randomUUID(), Instant.now(),
                orderId, user.getId(), event.getId()));

        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertEquals(2, tickets.size());
        tickets.forEach(ticket -> assertEquals(user.getId(), ticket.getUserId()));

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        assertNotNull(payment, "saga should have settled a payment");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(total, payment.getAmount());

        assertTrue(!notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty(),
                "order lifecycle notifications expected");
    }

    @Test
    void duplicateProcessPaymentCommand_isIdempotentWithoutRollbackOnlyFailure() {
        UUID orderId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("100.00");

        commandBus.send(new ProcessPaymentCommand(UUID.randomUUID(), Instant.now(),
                orderId, "STRIPE", total));

        Payment first = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, first.getStatus());

        // A redelivered/duplicate command must not poison the command handler
        // transaction (UnexpectedRollbackException) and must not change state.
        commandBus.send(new ProcessPaymentCommand(UUID.randomUUID(), Instant.now(),
                orderId, "STRIPE", total));

        Payment after = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, after.getStatus());
        assertEquals(total, after.getAmount());
    }

    private AppUser createUser() {
        AppUser user = new AppUser();
        user.setUsername("tester-" + UUID.randomUUID());
        user.setEmail("tester-" + UUID.randomUUID() + "@mail.com");
        user.setPassword("$2a$10$dummyhash");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private Event createEvent() {
        Event event = new Event();
        event.setName("Test Event");
        event.setCity("Lima");
        event.setVenue("Test Venue");
        event.setEventDate(LocalDateTime.now().plusDays(10));
        event.setBasePrice(new BigDecimal("100.00"));
        event.setTotalCapacity(100);
        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }
}
