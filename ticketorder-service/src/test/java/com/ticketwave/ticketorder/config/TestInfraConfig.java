package com.ticketwave.ticketorder.config;

import com.ticketwave.ticketorder.infrastructure.gateway.FakeEventGateway;
import com.ticketwave.ticketorder.infrastructure.gateway.FakeFraudGateway;
import com.ticketwave.ticketorder.infrastructure.gateway.FakePromotionGateway;
import com.ticketwave.ticketorder.infrastructure.gateway.FakeUserGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test infrastructure: in-memory bus doubles and in-memory gateway fakes so the
 * application context loads offline without a broker or the monolith.
 */
@Configuration
@Profile("test")
public class TestInfraConfig {

    @Bean
    public FakeEventGateway fakeEventGateway() {
        return new FakeEventGateway();
    }

    @Bean
    public FakePromotionGateway fakePromotionGateway() {
        return new FakePromotionGateway();
    }

    @Bean
    public FakeUserGateway fakeUserGateway() {
        return new FakeUserGateway();
    }

    @Bean
    public FakeFraudGateway fakeFraudGateway() {
        return new FakeFraudGateway();
    }
}
