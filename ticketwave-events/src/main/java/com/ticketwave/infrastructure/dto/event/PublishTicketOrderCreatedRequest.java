package com.ticketwave.infrastructure.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PublishTicketOrderCreatedRequest(
        @NotNull UUID orderId,
        @NotNull UUID userId,
        @NotNull UUID eventId,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal total,
        BigDecimal discount
) {
}
