package com.ticketwave.ticketorder.infrastructure.bus;

import com.ticketwave.ticketorder.domain.bus.CommandBus;
import com.ticketwave.ticketorder.domain.commands.Command;
import com.ticketwave.ticketorder.domain.commands.CommandHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory CommandBus for local development, active when no RabbitMQ broker is
 * configured (the local profile). Commands are dispatched synchronously to all
 * registered subscribers within the process.
 */
public class InMemoryCommandBus implements CommandBus {

    private final Map<Class<?>, List<Consumer<Command>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void send(Command command) {
        for (Consumer<Command> consumer : handlers.getOrDefault(command.getClass(), List.of())) {
            consumer.accept(command);
        }
    }

    @Override
    public <C extends Command> void subscribe(Class<C> commandType, CommandHandler<C> handler) {
        handlers.computeIfAbsent(commandType, k -> new CopyOnWriteArrayList<>())
                .add(command -> handler.handle(commandType.cast(command)));
    }
}
