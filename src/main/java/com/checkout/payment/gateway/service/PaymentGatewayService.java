package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankApiResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankApiService bankApiService;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankApiService bankApiService) {
    this.paymentsRepository = paymentsRepository;
    this.bankApiService = bankApiService;
  }

  // Return optional as payment not found is valid case
  public Optional<PaymentResponse> getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id);
  }

  public PaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    final UUID paymentRequestId = UUID.randomUUID();
    LOG.debug("Requesting processing payment with ID {}", paymentRequestId);

    final PaymentResponse.PaymentResponseBuilder responseBuilder = PaymentResponse.builder()
        .id(paymentRequestId)
        .cardNumberLastFour(paymentRequest.getCardNumberLastFour())
        .expiryMonth(paymentRequest.expiryMonth())
        .expiryYear(paymentRequest.expiryYear())
        .currency(paymentRequest.currency())
        .amount(paymentRequest.amount());
    try {
      // Submit the payment and get the status from the upstream payment processor and
      // extract the status from the response - we will only persist authorized / declined payments.
      responseBuilder.status(getPaymentStatusForRequest(paymentRequestId, this.bankApiService.authorise(paymentRequest)));
      // Persist the event for the success case
      paymentsRepository.save(responseBuilder.build());
    }
    catch (final HttpClientErrorException | HttpServerErrorException ex) {
      HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
      LOG.error("Something went wrong calling the payments api. The status code was: {}, the reason was: {}",
          status, ex.getMessage());
      // Re-throw and wrap as our exception type so global error handler can handle it.
      throw new EventProcessingException(ex.getMessage(), paymentRequestId, status);
    } catch (final RestClientException ex) {
      // Fallback for other Rest exceptions that could feasibly occur such as ResourceAccessException, UnknownHttpStatusCodeException
      LOG.error("An unexpected exception occurred calling the payments api. The reason was: {}", ex.getMessage());
      // Re-throw and wrap as our exception type so global error handler can handle it.
      throw new EventProcessingException(ex.getMessage(), paymentRequestId, INTERNAL_SERVER_ERROR);
    }

    return responseBuilder.build();
  }

  private static PaymentStatus getPaymentStatusForRequest(final UUID requestId, final ResponseEntity<BankApiResponse> authorizationResponse) {
    if (!authorizationResponse.getStatusCode().is2xxSuccessful() || authorizationResponse.getBody() == null) {
      // If we don't get a 200 response or the response body is null we want to throw an exception with
      // the status code returned by the third party.
      final String message = "Something went wrong calling the payments api, the status code was %s"
          .formatted(authorizationResponse.getStatusCode().value());
      LOG.error(message);
      throw new EventProcessingException(message, requestId, BAD_GATEWAY);
    }
    return authorizationResponse.getBody().getPaymentStatus();
  }
}
