package com.ticketwave.ticketorder.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketwave.ticketorder.domain.bus.CommandBus;
import com.ticketwave.ticketorder.domain.bus.EventBus;
import com.ticketwave.ticketorder.domain.commands.CancelTicketOrderCommand;
import com.ticketwave.ticketorder.domain.commands.IssueTicketCommand;
import com.ticketwave.ticketorder.domain.commands.NotifyOrderCommand;
import com.ticketwave.ticketorder.domain.commands.ProcessPaymentCommand;
import com.ticketwave.ticketorder.domain.commands.RefundPaymentCommand;
import com.ticketwave.ticketorder.domain.events.EventCancelled;
import com.ticketwave.ticketorder.domain.events.EventCreated;
import com.ticketwave.ticketorder.domain.events.EventUpdated;
import com.ticketwave.ticketorder.domain.events.FraudDetected;
import com.ticketwave.ticketorder.domain.events.NotificationFailed;
import com.ticketwave.ticketorder.domain.events.NotificationSent;
import com.ticketwave.ticketorder.domain.events.PaymentAuthorized;
import com.ticketwave.ticketorder.domain.events.PaymentFailed;
import com.ticketwave.ticketorder.domain.events.PromotionApplied;
import com.ticketwave.ticketorder.domain.events.TicketDeliveryFailed;
import com.ticketwave.ticketorder.domain.events.TicketIssued;
import com.ticketwave.ticketorder.domain.events.TicketOrderCancelled;
import com.ticketwave.ticketorder.domain.events.TicketOrderCompleted;
import com.ticketwave.ticketorder.domain.events.TicketOrderConfirmed;
import com.ticketwave.ticketorder.domain.events.TicketOrderCreated;
import com.ticketwave.ticketorder.domain.events.TicketRefunded;
import com.ticketwave.ticketorder.infrastructure.bus.InMemoryCommandBus;
import com.ticketwave.ticketorder.infrastructure.bus.InMemoryEventBus;
import com.ticketwave.ticketorder.infrastructure.bus.RabbitMQCommandBusAdapter;
import com.ticketwave.ticketorder.infrastructure.bus.RabbitMQEventBusAdapter;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ is the shared cross-service bus. The polymorphic type validator only
 * allows the shared contract packages, so a message published by the monolith
 * (e.g. PaymentAuthorized) is deserialized into the identical record type this
 * service keeps on its own classpath.
 */
@Configuration
public class EventBusConfig {

    @Bean
    @Profile("!rabbitmq")
    public EventBus inMemoryEventBus() {
        return new InMemoryEventBus();
    }

    @Bean
    @Profile("!rabbitmq")
    public CommandBus inMemoryCommandBus() {
        return new InMemoryCommandBus();
    }

    @Bean
    @Profile("rabbitmq")
    public EventBus rabbitMqEventBus(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin) {
        return new RabbitMQEventBusAdapter(rabbitTemplate, amqpAdmin);
    }

    @Bean
    @Profile("rabbitmq")
    public CommandBus rabbitMqCommandBus(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin) {
        return new RabbitMQCommandBusAdapter(rabbitTemplate, amqpAdmin);
    }

    @Bean
    @Profile("rabbitmq")
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(busObjectMapper());
        converter.setJavaTypeMapper(sharedContractTypeMapper());
        return converter;
    }

    /**
     * The monolith keeps the shared contract under {@code com.ticketwave.domain.*}
     * while this service re-hosts it under {@code com.ticketwave.ticketorder.domain.*}.
     * Map the monolith type ids to the local classes so queued messages resolve;
     * the reverse mapping (built automatically) makes this service publish with
     * the shared ids the monolith expects.
     */
    private static Jackson2JavaTypeMapper sharedContractTypeMapper() {
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("com.ticketwave.domain.commands.CancelTicketOrderCommand", CancelTicketOrderCommand.class);
        idClassMapping.put("com.ticketwave.domain.commands.IssueTicketCommand", IssueTicketCommand.class);
        idClassMapping.put("com.ticketwave.domain.commands.NotifyOrderCommand", NotifyOrderCommand.class);
        idClassMapping.put("com.ticketwave.domain.commands.ProcessPaymentCommand", ProcessPaymentCommand.class);
        idClassMapping.put("com.ticketwave.domain.commands.RefundPaymentCommand", RefundPaymentCommand.class);
        idClassMapping.put("com.ticketwave.domain.events.EventCancelled", EventCancelled.class);
        idClassMapping.put("com.ticketwave.domain.events.EventCreated", EventCreated.class);
        idClassMapping.put("com.ticketwave.domain.events.EventUpdated", EventUpdated.class);
        idClassMapping.put("com.ticketwave.domain.events.FraudDetected", FraudDetected.class);
        idClassMapping.put("com.ticketwave.domain.events.NotificationFailed", NotificationFailed.class);
        idClassMapping.put("com.ticketwave.domain.events.NotificationSent", NotificationSent.class);
        idClassMapping.put("com.ticketwave.domain.events.PaymentAuthorized", PaymentAuthorized.class);
        idClassMapping.put("com.ticketwave.domain.events.PaymentFailed", PaymentFailed.class);
        idClassMapping.put("com.ticketwave.domain.events.PromotionApplied", PromotionApplied.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketDeliveryFailed", TicketDeliveryFailed.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketIssued", TicketIssued.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketOrderCancelled", TicketOrderCancelled.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketOrderCompleted", TicketOrderCompleted.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketOrderConfirmed", TicketOrderConfirmed.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketOrderCreated", TicketOrderCreated.class);
        idClassMapping.put("com.ticketwave.domain.events.TicketRefunded", TicketRefunded.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.commands.CancelTicketOrderCommand", CancelTicketOrderCommand.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.commands.IssueTicketCommand", IssueTicketCommand.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.commands.NotifyOrderCommand", NotifyOrderCommand.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.commands.ProcessPaymentCommand", ProcessPaymentCommand.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.commands.RefundPaymentCommand", RefundPaymentCommand.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.EventCancelled", EventCancelled.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.EventCreated", EventCreated.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.EventUpdated", EventUpdated.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.FraudDetected", FraudDetected.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.NotificationFailed", NotificationFailed.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.NotificationSent", NotificationSent.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.PaymentAuthorized", PaymentAuthorized.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.PaymentFailed", PaymentFailed.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.PromotionApplied", PromotionApplied.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketDeliveryFailed", TicketDeliveryFailed.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketIssued", TicketIssued.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketOrderCancelled", TicketOrderCancelled.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketOrderCompleted", TicketOrderCompleted.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketOrderConfirmed", TicketOrderConfirmed.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketOrderCreated", TicketOrderCreated.class);
        idClassMapping.put("com.ticketwave.ticketorder.domain.events.TicketRefunded", TicketRefunded.class);
        idClassMapping.put(List.class.getName(), List.class);
        idClassMapping.put(Map.class.getName(), Map.class);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(idClassMapping);
        return typeMapper;
    }

    private static ObjectMapper busObjectMapper() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ticketwave.ticketorder.domain.events.")
                .allowIfSubType("com.ticketwave.ticketorder.domain.commands.")
                .allowIfBaseType("java.lang.")
                .allowIfBaseType("java.math.")
                .allowIfBaseType("java.time.")
                .allowIfBaseType("java.util.")
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL, com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
