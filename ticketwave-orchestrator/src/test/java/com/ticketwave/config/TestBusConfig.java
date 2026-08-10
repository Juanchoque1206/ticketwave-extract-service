package com.ticketwave.config;

import com.ticketwave.domain.bus.CommandBus;
import com.ticketwave.domain.bus.EventBus;
import com.ticketwave.infrastructure.bus.InMemoryCommandBus;
import com.ticketwave.infrastructure.bus.InMemoryEventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * In-memory bus doubles for the test profile, matching the production RabbitMQ
 * transport so the saga orchestrator can be exercised offline.
 */
@Configuration
@Profile("test")
public class TestBusConfig {

    @Bean
    public EventBus eventBus() {
        return new InMemoryEventBus();
    }

    @Bean
    public CommandBus commandBus() {
        return new InMemoryCommandBus();
    }
}
