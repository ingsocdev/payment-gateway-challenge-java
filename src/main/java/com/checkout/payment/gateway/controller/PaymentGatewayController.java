package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping("/payments")
  public ResponseEntity<PaymentResponse> postPayment(@Valid @RequestBody PostPaymentRequest request) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(request), HttpStatus.OK);
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<PaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    // Handle optional or payment not found
    return paymentGatewayService.getPaymentById(id).map(postPaymentResponse ->
        new ResponseEntity<>(postPaymentResponse, HttpStatus.OK))
        .orElseThrow(() -> new PaymentNotFoundException("Payment %s not found".formatted(id)));
  }
}
