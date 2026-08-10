package com.ticketwave.integration;

import com.ticketwave.domain.bus.CommandBus;
import com.ticketwave.domain.bus.EventBus;
import com.ticketwave.domain.commands.IssueTicketCommand;
import com.ticketwave.domain.commands.NotifyOrderCommand;
import com.ticketwave.domain.commands.ProcessPaymentCommand;
import com.ticketwave.domain.events.NotificationSent;
import com.ticketwave.domain.events.PaymentAuthorized;
import com.ticketwave.domain.events.TicketIssued;
import com.ticketwave.domain.events.TicketOrderCompleted;
import com.ticketwave.domain.events.TicketOrderCreated;
import com.ticketwave.domain.saga.SagaState;
import com.ticketwave.domain.saga.SagaStateRepository;
import com.ticketwave.domain.saga.SagaStatus;
import com.ticketwave.domain.saga.SagaStep;
import com.ticketwave.domain.saga.TicketOrderSagaOrchestrator;
import com.ticketwave.infrastructure.bus.RabbitMQCommandBusAdapter;
import com.ticketwave.infrastructure.bus.RabbitMQEventBusAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end test against the real infrastructure (RabbitMQ + Redis on
 * localhost, as defined in application.yml): verifies that events are received
 * and commands published over the actual broker, and that saga snapshots
 * persisted in Redis survive an orchestrator stop and are re-driven by
 * {@link TicketOrderSagaOrchestrator#recover()}.
 */
@SpringBootTest
@TestPropertySource(properties = "ticketwave.saga.recovery-enabled=false")
class RabbitMqRedisIntegrationTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private CommandBus commandBus;
    @Autowired
    private SagaStateRepository sagaRepository;
    @Autowired
    private TicketOrderSagaOrchestrator orchestrator;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitListenerEndpointRegistry rabbitRegistry;

    private final List<Object> commands = new CopyOnWriteArrayList<>();
    private final List<Object> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        flushRedis();
        purgeQueues();
        commands.clear();
        events.clear();
        commandBus.subscribe(ProcessPaymentCommand.class, commands::add);
        commandBus.subscribe(IssueTicketCommand.class, commands::add);
        commandBus.subscribe(NotifyOrderCommand.class, commands::add);
        eventBus.subscribe(TicketOrderCompleted.class, events::add);
    }

    @Test
    void happyPath_receivesEventsAndPublishesCommandsOverRabbitMq() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("100.00");

        eventBus.publish(new TicketOrderCreated(UUID.randomUUID(), Instant.now(),
                orderId, userId, eventId, 2, total, BigDecimal.ZERO));

        ProcessPaymentCommand payment = await(commands, ProcessPaymentCommand.class,
                c -> c.orderId().equals(orderId));
        assertEquals(total, payment.amount());
        assertEquals("STRIPE", payment.provider());

        eventBus.publish(new PaymentAuthorized(UUID.randomUUID(), Instant.now(),
                orderId, userId, total, "TXN-1"));
        IssueTicketCommand issue = await(commands, IssueTicketCommand.class,
                c -> c.orderId().equals(orderId));
        assertEquals(2, issue.quantity());

        eventBus.publish(new TicketIssued(UUID.randomUUID(), Instant.now(),
                orderId, userId, eventId, List.of(UUID.randomUUID())));
        NotifyOrderCommand notify = await(commands, NotifyOrderCommand.class,
                c -> c.orderId().equals(orderId));
        assertEquals(orderId, notify.orderId());

        eventBus.publish(new NotificationSent(UUID.randomUUID(), Instant.now(),
                orderId, userId, UUID.randomUUID()));
        TicketOrderCompleted completed = await(events, TicketOrderCompleted.class,
                e -> e.orderId().equals(orderId));
        assertEquals(total, completed.total());

        SagaState state = sagaRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(SagaStatus.COMPLETED, state.status());
    }

    @Test
    void orchestratorCrashBetweenSaveAndSend_isResumedFromRedis() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        SagaState seeded = SagaState.start(orderId, userId, eventId, 2, new BigDecimal("100.00"))
                .progress(SagaStep.PAYMENT_PROCESSED);
        sagaRepository.save(seeded);

        SagaState fromRedis = sagaRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(SagaStatus.RUNNING, fromRedis.status());
        assertEquals(SagaStep.PAYMENT_PROCESSED, fromRedis.currentStep());

        orchestrator.recover();

        IssueTicketCommand issue = await(commands, IssueTicketCommand.class,
                c -> c.orderId().equals(orderId));
        assertEquals(2, issue.quantity());
        assertEquals(userId, issue.userId());
    }

    @Test
    void orchestratorDown_missesEventsButRecoversFromRedis() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        eventBus.publish(new TicketOrderCreated(UUID.randomUUID(), Instant.now(),
                orderId, userId, eventId, 2, new BigDecimal("100.00"), BigDecimal.ZERO));
        await(commands, ProcessPaymentCommand.class, c -> c.orderId().equals(orderId));

        stopAllListeners();
        eventBus.publish(new PaymentAuthorized(UUID.randomUUID(), Instant.now(),
                orderId, userId, new BigDecimal("100.00"), "TXN-1"));

        Thread.sleep(500);
        SagaState whileDown = sagaRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(SagaStatus.CREATED, whileDown.status(),
                "event published while the orchestrator is down must not advance the saga");

        amqpAdmin.purgeQueue(RabbitMQEventBusAdapter.QUEUE);

        startAllListeners();
        orchestrator.recover();

        awaitCondition(() -> commands.stream()
                .filter(ProcessPaymentCommand.class::isInstance)
                .map(ProcessPaymentCommand.class::cast)
                .filter(c -> c.orderId().equals(orderId))
                .count() >= 2, "recover() must re-send the pending ProcessPaymentCommand");
    }

    @SuppressWarnings("unchecked")
    private <T> T await(List<?> received, Class<T> type, java.util.function.Predicate<T> matcher)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            for (Object o : received) {
                if (type.isInstance(o)) {
                    T value = (T) o;
                    if (matcher.test(value)) {
                        return value;
                    }
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timeout waiting for " + type.getSimpleName());
    }

    private void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("timeout: " + description);
    }

    private void stopAllListeners() throws InterruptedException {
        for (MessageListenerContainer container : rabbitRegistry.getListenerContainers()) {
            container.stop();
        }
        for (MessageListenerContainer container : rabbitRegistry.getListenerContainers()) {
            while (container.isRunning()) {
                Thread.sleep(25);
            }
        }
    }

    private void startAllListeners() throws InterruptedException {
        for (MessageListenerContainer container : rabbitRegistry.getListenerContainers()) {
            container.start();
        }
        for (MessageListenerContainer container : rabbitRegistry.getListenerContainers()) {
            long deadline = System.currentTimeMillis() + 10_000;
            while (!container.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
        }
    }

    private void flushRedis() {
        Set<String> stateKeys = redis.keys("saga:state:*");
        if (stateKeys != null) {
            redis.delete(stateKeys);
        }
        Set<String> indexKeys = redis.keys("saga:order:*");
        if (indexKeys != null) {
            redis.delete(indexKeys);
        }
    }

    private void purgeQueues() {
        amqpAdmin.purgeQueue(RabbitMQEventBusAdapter.QUEUE);
        amqpAdmin.purgeQueue(RabbitMQCommandBusAdapter.QUEUE);
    }
}
