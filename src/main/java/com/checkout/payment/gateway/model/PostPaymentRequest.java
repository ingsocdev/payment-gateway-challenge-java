package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.validation.ValidExpiryDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import java.io.Serializable;

@Builder(toBuilder = true)
@ValidExpiryDate
public record PostPaymentRequest(@NotNull @Size(min = 14, max = 19) @Pattern(regexp = "^[0-9]*$")
                                 @JsonProperty("card_number") String cardNumber,
                                 @Min(1) @Max(12) @JsonProperty("expiry_month") int expiryMonth,
                                 @Min(2000) @JsonProperty("expiry_year") int expiryYear,
                                 @NotNull Currency currency,
                                 @Min(1) int amount,
                                 @NotNull @Size(min = 3, max = 4) @Pattern(regexp = "^[0-9]*$") String cvv)
    implements Serializable {

  public String getCardNumberLastFour() {
    return cardNumber != null && cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : "";
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + (cardNumber != null && cardNumber.length() >= 4 ? getCardNumberLastFour() : "") +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=***" +
        '}';
  }
}
