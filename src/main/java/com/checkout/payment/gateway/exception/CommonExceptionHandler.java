package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.RejectedPaymentResponse;
import com.checkout.payment.gateway.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<String> handleException(PaymentNotFoundException ex) {
    LOG.debug(ex.getMessage(), ex);

    return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<RejectedPaymentResponse> handleException(HttpMessageNotReadableException ex) {
    final UUID requestId = UUID.randomUUID();
    LOG.debug("Request {} could not be deserialized", requestId, ex);

    return new ResponseEntity<>(RejectedPaymentResponse.builder()
        .requestId(requestId)
        .status(PaymentStatus.REJECTED)
        .message("The request was invalid").build(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<RejectedPaymentResponse> handleException(EventProcessingException ex) {
    LOG.error("An error occurred calling the payment provider", ex);

    return new ResponseEntity<>(RejectedPaymentResponse.builder()
        .requestId(ex.getRequestId())
        .status(PaymentStatus.REJECTED)
        .message("An error occurred calling the payment provider").build(), ex.getStatusCode());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handleException(MethodArgumentNotValidException ex) {
    LOG.debug("Request validation failed", ex);

    // Extract the request that failed validation
    Object target = ex.getBindingResult().getTarget();
    if (target instanceof PostPaymentRequest) {
      final RejectedPaymentResponse rejectedPaymentResponse = RejectedPaymentResponse.builder()
          .requestId(UUID.randomUUID())
          .status(PaymentStatus.REJECTED)
          .message("The request was invalid")
          .errors(extractValidationErrors(ex)).build();

      return new ResponseEntity<>(rejectedPaymentResponse, HttpStatus.BAD_REQUEST);
    }

    // Fallback error response
    return new ResponseEntity<>(ErrorResponse.builder().message(ex.getMessage()).build(), ex.getStatusCode());
  }

  // Extract the specific validation errors
  private List<ValidationError> extractValidationErrors(final MethodArgumentNotValidException ex) {
    final List<ValidationError> errors = new ArrayList<>();

    // Extract Field-level errors (e.g. @Min, @NotNull)
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.add(new ValidationError(error.getField(), error.getDefaultMessage()));
    }
    // Extract Class-level errors
    for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
      errors.add(new ValidationError("paymentRequest", error.getDefaultMessage()));
    }

    return errors;
  }
}
