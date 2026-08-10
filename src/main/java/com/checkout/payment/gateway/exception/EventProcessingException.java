package com.checkout.payment.gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Getter
public class EventProcessingException extends RuntimeException {

  private final HttpStatus statusCode;
  private final UUID requestId;

  public EventProcessingException(String message, UUID requestId, HttpStatus statusCode) {
    super(message);
    this.statusCode = statusCode;
    this.requestId = requestId;
  }
}
