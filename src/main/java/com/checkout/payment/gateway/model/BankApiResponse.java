package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BankApiResponse(boolean authorized, @JsonProperty("authorization_code") String authorizationCode) {
  public PaymentStatus getPaymentStatus() {
    return authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }
}
