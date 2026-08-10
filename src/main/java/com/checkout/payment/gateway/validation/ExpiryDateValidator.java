package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.YearMonth;

public class ExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, PostPaymentRequest> {

  @Autowired
  private Clock clock;

  @Override
  public boolean isValid(PostPaymentRequest request, ConstraintValidatorContext context) {
    // Fallback to field validation
    if (request == null) {
      return true;
    }

    int month = request.expiryMonth();
    int year = request.expiryYear();

    // Fallback to field validation
    if (month < 1 || month > 12 || year < 2000) {
      return true;
    }

    YearMonth expiry = YearMonth.of(year, month);
    YearMonth current = YearMonth.now(clock);

    return !expiry.isBefore(current);
  }
}
