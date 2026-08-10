package com.checkout.payment.gateway.model;

import jakarta.annotation.Nullable;
import lombok.Builder;
import java.util.List;

@Builder(toBuilder = true)
public record ErrorResponse(String message, @Nullable List<ValidationError> errors) {}
