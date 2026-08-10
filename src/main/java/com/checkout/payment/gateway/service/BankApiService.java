package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.BankApiResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BankApiService {

  private final RestTemplate restTemplate;

  public BankApiService(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public ResponseEntity<BankApiResponse> authorise(final PostPaymentRequest paymentRequest) {
    return this.restTemplate.postForEntity("/payments", paymentRequest, BankApiResponse.class);
  }
}
