package com.ticketwave.ticketorder.domain.commands;

import java.time.Instant;
import java.util.UUID;

public record IssueTicketCommand(
        UUID commandId,
        Instant issuedAt,
        UUID orderId,
        UUID userId,
        UUID eventId,
        int quantity) implements Command {
}
