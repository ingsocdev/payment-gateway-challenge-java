package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import lombok.Builder;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record RejectedPaymentResponse(UUID requestId, PaymentStatus status, String message,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<ValidationError> errors) {}
