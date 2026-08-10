package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import lombok.Builder;
import java.util.UUID;

@Builder(toBuilder = true)
public record PaymentResponse(UUID id, PaymentStatus status, String cardNumberLastFour, int expiryMonth,
                              int expiryYear, Currency currency, int amount) {}
