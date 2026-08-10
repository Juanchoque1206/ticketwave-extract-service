package com.ticketwave;

import com.ticketwave.domain.bus.EventBus;
import com.ticketwave.domain.events.TicketOrderCreated;
import com.ticketwave.infrastructure.bus.RabbitMQEventBusAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that with the rabbitmq profile active the EventBus bean is the
 * RabbitMQEventBusAdapter and that publish() really routes the event onto the
 * ticketwave.events exchange. A dedicated test queue bound to that exchange is
 * used to read the message back, so the verification does not race other
 * consumers (e.g. a running monolith instance) on the shared durable queue.
 * Requires a RabbitMQ instance on localhost:5672 (see docker-compose.yml in the
 * ticketwave-event-bus module).
 */
@SpringBootTest
@ActiveProfiles({"test", "rabbitmq"})
class RabbitMQEventBusRoundTripTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    void eventBusBean_isRabbitMqAdapter() {
        assertInstanceOf(RabbitMQEventBusAdapter.class, eventBus);
    }

    @Test
    void publishTicketOrderCreated_isRoutedOnTheEventsExchange() {
        String testQueue = "ticketwave.events.test." + UUID.randomUUID();
        Queue queue = new Queue(testQueue, false, true, true);
        Binding binding = BindingBuilder.bind(queue)
                .to(new org.springframework.amqp.core.TopicExchange(RabbitMQEventBusAdapter.EXCHANGE))
                .with("TicketOrderCreated");
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        try {
            TicketOrderCreated event = new TicketOrderCreated(
                    UUID.randomUUID(),
                    Instant.now(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    new BigDecimal("100.00"),
                    BigDecimal.ZERO);

            eventBus.publish(event);

            TicketOrderCreated received = (TicketOrderCreated) rabbitTemplate
                    .receiveAndConvert(testQueue, 10_000);

            assertNotNull(received, "published event should be delivered to the exchange");
            assertEquals(event.id(), received.id());
            assertEquals(event.orderId(), received.orderId());
            assertEquals(event.quantity(), received.quantity());
        } finally {
            amqpAdmin.removeBinding(binding);
            amqpAdmin.deleteQueue(testQueue);
        }
    }
}
