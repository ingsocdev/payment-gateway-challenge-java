package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  // Use a concurrent hash map as we need to consider concurrent get / put operations
  private final Map<UUID, PaymentResponse> payments = new ConcurrentHashMap<>();

  public void save(PaymentResponse payment) {
    // A duplicate id should not overwrite the existing value
    if (payments.putIfAbsent(payment.id(), payment) != null) {
      throw new IllegalStateException("Payment ID already exists");
    }
  }

  public Optional<PaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
