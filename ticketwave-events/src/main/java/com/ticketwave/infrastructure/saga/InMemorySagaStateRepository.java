package com.ticketwave.infrastructure.saga;

import com.ticketwave.domain.saga.SagaState;
import com.ticketwave.domain.saga.SagaStateRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory SagaStateRepository, active under the local profile so the saga
 * orchestrator and the recovery job run without a Redis connection.
 */
@Component
@Profile("local")
public class InMemorySagaStateRepository implements SagaStateRepository {

    private final Map<UUID, SagaState> states = new ConcurrentHashMap<>();

    @Override
    public void save(SagaState state) {
        states.put(state.sagaId(), state);
    }

    @Override
    public Optional<SagaState> findById(UUID sagaId) {
        return Optional.ofNullable(states.get(sagaId));
    }

    @Override
    public Optional<SagaState> findByOrderId(UUID orderId) {
        return states.values().stream()
                .filter(state -> orderId.equals(state.orderId()))
                .findFirst();
    }

    @Override
    public List<SagaState> findAll() {
        return new ArrayList<>(states.values());
    }

    @Override
    public void deleteById(UUID sagaId) {
        states.remove(sagaId);
    }
}
