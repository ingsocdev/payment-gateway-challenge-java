package com.checkout.payment.gateway.controller;

import static com.checkout.payment.gateway.model.Currency.GBP;
import static com.checkout.payment.gateway.model.Currency.USD;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.Currency;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.RejectedPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;

  @Autowired
  private ObjectMapper mapper;

  @MockBean
  private Clock clock;

  private static final String VALID_CARDNUMBER = "0000000000000000001";
  private static final int VALID_EXPIRY_MONTH = 8;
  private static final int VALID_EXPIRY_YEAR = 2026;
  private static final Currency VALID_CURRENCY = USD;
  private static final int VALID_AMOUNT = 10;
  private static final String VALID_CVV = "123";
  private static final String VALID_LAST_FOUR_DIGITS = "0000";
  
  private static final String PAYMENTS_URI = "/payments";

  @BeforeEach
  void setUpTime() {
    // Set a stable clock for expiry month / year testing
    final Clock fixedClock = Clock.fixed(
        Instant.parse("2026-08-01T00:00:00Z"),
        ZoneId.of("UTC")
    );
    when(clock.instant()).thenReturn(fixedClock.instant());
    when(clock.getZone()).thenReturn(fixedClock.getZone());
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    var payment = PaymentResponse.builder()
        .id(UUID.randomUUID())
        .status(PaymentStatus.AUTHORIZED)
        .cardNumberLastFour(VALID_LAST_FOUR_DIGITS)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(USD)
        .amount(VALID_AMOUNT).build();

    paymentsRepository.save(payment);
    assertRequestPersisted(payment);
  }

  @Test
  void saveThrowsExceptionIfPaymentWithSameId() throws Exception {
    final UUID paymentId = UUID.randomUUID();
    var payment = PaymentResponse.builder()
        .id(paymentId)
        .status(PaymentStatus.AUTHORIZED)
        .cardNumberLastFour(VALID_LAST_FOUR_DIGITS)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(USD)
        .amount(VALID_AMOUNT).build();
    paymentsRepository.save(payment);

    var secondPayment = PaymentResponse.builder()
        .id(paymentId)
        .status(PaymentStatus.AUTHORIZED)
        .cardNumberLastFour(VALID_LAST_FOUR_DIGITS)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(GBP)
        .amount(20).build();

    assertThrows(IllegalStateException.class, () -> paymentsRepository.save(secondPayment));
    assertRequestPersisted(payment);
  }

  @ParameterizedTest
  @MethodSource("provideValidPayments")
  void validPaymentsAreSentAndCanBeRetrievedAndResultPersisted(final PostPaymentRequest paymentRequest, final PaymentStatus expectedStatus) throws Exception {
    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(paymentRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(expectedStatus.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(paymentRequest.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(paymentRequest.expiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(paymentRequest.expiryYear()))
        .andExpect(jsonPath("$.currency").value(paymentRequest.currency().name()))
        .andExpect(jsonPath("$.amount").value(paymentRequest.amount()))
        .andExpect(jsonPath("$.error").doesNotExist()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
    assertRequestPersisted(response);
  }

  @Test
  void retrievePaymentNotFound() throws Exception {
    var paymentId = UUID.randomUUID();
    mvc.perform(MockMvcRequestBuilders.get(PAYMENTS_URI + "/" + paymentId))
        .andExpect(status().isNotFound())
        .andExpect(content().string("Payment %s not found".formatted(paymentId)));
  }

  @Test
  void serviceGracefullyHandlesUpstreamServiceUnavailableAndDoesNotPersistPayment() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber("0000000000000000000")
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.message").value("An error occurred calling the payment provider"))
        .andExpect(jsonPath("$.error").doesNotExist()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @ParameterizedTest
  @MethodSource("provideCardNumber")
  void invalidCardNumbersResultInBadRequestAndResultNotPersisted(final String cardNumber, final String expectedMessage) throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(cardNumber)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "cardNumber", expectedMessage, 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @ParameterizedTest
  @MethodSource("provideExpiryMonth")
  void invalidExpiryMonthResultInBadRequestAndResultNotPersisted(final int expiryMonth, final String expectedMessage) throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(expiryMonth)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "expiryMonth", expectedMessage, 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @ParameterizedTest
  @MethodSource("provideExpiryYear")
  void invalidExpiryYearResultInBadRequestAndResultNotPersisted(final int expiryYear, final String expectedMessage) throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(expiryYear)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "expiryYear", expectedMessage, 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void invalidCurrencyResultsInBadRequestAndResultNotPersisted() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(null)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "currency",
        "must not be null", 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void handleInvalidCurrencyJsonRequest() throws Exception {
    String request = """
    {
      "card_number": "0000000000000000001",
      "expiry_month": 8,
      "expiry_year": 2027,
      "currency": "JPY",
      "amount": 10,
      "cvv": "123"
    }
    """;

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request))
        .andExpect(status().isBadRequest()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void handleInvalidAmountJsonRequest() throws Exception {
    String request = """
    {
      "card_number": "0000000000000000001",
      "expiry_month": 8,
      "expiry_year": 2027,
      "currency": "GBP",
      "amount": 10.5,
      "cvv": "123"
    }
    """;

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isBadRequest()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void handleInvalidEnumJsonRequest() throws Exception {
    String request = """
    {
      "card_number": "0000000000000000001",
      "expiry_month": 8,
      "expiry_year": 2027,
      "currency": 1,
      "amount": 10,
      "cvv": "123"
    }
    """;

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isBadRequest()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void invalidAmountResultsInBadRequestAndResultNotPersisted(final int amount) throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(amount)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "amount",
        "must be greater than or equal to 1", 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @ParameterizedTest
  @MethodSource("provideCvv")
  void invalidCVVResultsInBadRequestAndResultNotPersisted(final String cvv, final String expectedMessage) throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(cvv).build();

    var result = assertResponseFieldsAndError(request, "cvv", expectedMessage, 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void multipleValidationErrorsResultsInBadRequestAndResultNotPersisted() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(null)
        .expiryMonth(0)
        .expiryYear(0)
        .currency(null)
        .amount(-1)
        .cvv(null).build();

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.errors.length()").value(6)).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void expiryMonthBeforeCurrentResultsInBadRequestAndResultNotPersisted() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(7)
        .expiryYear(2026)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "paymentRequest",
        "Card expiration date cannot be in the past", 1);
    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }

  @Test
  void validMonthIsSuccessfulAndResultIsPersisted() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(9)
        .expiryYear(2026)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(request.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(request.expiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(request.expiryYear()))
        .andExpect(jsonPath("$.currency").value(request.currency().name()))
        .andExpect(jsonPath("$.amount").value(request.amount()))
        .andExpect(jsonPath("$.error").doesNotExist()).andReturn();

    var response = mapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);

    assertRequestPersisted(response);
  }

  @Test
  void expiredCardIsRejectedAndResultNotPersisted() throws Exception {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(12)
        .expiryYear(2025)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    var result = assertResponseFieldsAndError(request, "paymentRequest",
        "Card expiration date cannot be in the past", 1);

    var response = mapper.readValue(result.getResponse().getContentAsString(), RejectedPaymentResponse.class);

    assertRequestNotPersisted(response);
  }


  private static Stream<Arguments> provideValidPayments() {
    var request = PostPaymentRequest.builder()
        .cardNumber(VALID_CARDNUMBER)
        .expiryMonth(VALID_EXPIRY_MONTH)
        .expiryYear(VALID_EXPIRY_YEAR)
        .currency(VALID_CURRENCY)
        .amount(VALID_AMOUNT)
        .cvv(VALID_CVV).build();

    return Stream.of(
        Arguments.of(request, PaymentStatus.AUTHORIZED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000003").build(), PaymentStatus.AUTHORIZED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000005").build(), PaymentStatus.AUTHORIZED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000007").build(), PaymentStatus.AUTHORIZED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000009").build(), PaymentStatus.AUTHORIZED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000002").build(), PaymentStatus.DECLINED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000004").build(), PaymentStatus.DECLINED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000006").build(), PaymentStatus.DECLINED),
        Arguments.of(request.toBuilder().cardNumber("0000000000000000008").build(), PaymentStatus.DECLINED)
    );
  }


  private static Stream<Arguments> provideCardNumber() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of("", "size must be between 14 and 19"),
        Arguments.of("abcdefghijklmn", "must match \"^[0-9]*$\""),
        Arguments.of("123456", "size must be between 14 and 19"),
        Arguments.of("00000000000000000000", "size must be between 14 and 19")
    );
  }

  private static Stream<Arguments> provideExpiryMonth() {
    return Stream.of(
        Arguments.of(-1, "must be greater than or equal to 1"),
        Arguments.of(0, "must be greater than or equal to 1"),
        Arguments.of(13, "must be less than or equal to 12")
    );
  }

  private static Stream<Arguments> provideExpiryYear() {
    return Stream.of(
        Arguments.of(-1, "must be greater than or equal to 2000"),
        Arguments.of(0, "must be greater than or equal to 2000")
    );
  }

  private static Stream<Arguments> provideCvv() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of("", "size must be between 3 and 4"),
        Arguments.of("12", "size must be between 3 and 4"),
        Arguments.of("abc", "must match \"^[0-9]*$\""),
        Arguments.of("12345", "size must be between 3 and 4")
    );
  }

  private MvcResult assertResponseFieldsAndError(final PostPaymentRequest request, final String expectedFieldName,
      final String expectedMessage, final int numberOfErrors) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.post(PAYMENTS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()))
        .andExpect(jsonPath("$.message").value("The request was invalid"))
        .andExpect(jsonPath("$.errors[0].field").value(expectedFieldName))
        .andExpect(jsonPath("$.errors[0].message").value(expectedMessage))
        .andExpect(jsonPath("$.errors.length()").value(numberOfErrors))
        .andReturn();
  }

  void assertRequestPersisted(PaymentResponse response) throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(PAYMENTS_URI + "/" + response.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(response.status().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(response.cardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(response.expiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(response.expiryYear()))
        .andExpect(jsonPath("$.currency").value(response.currency().name()))
        .andExpect(jsonPath("$.amount").value(response.amount()));
  }

  void assertRequestNotPersisted(RejectedPaymentResponse response) throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(PAYMENTS_URI + "/" + response.requestId()))
        .andExpect(status().isNotFound());
  }
}
