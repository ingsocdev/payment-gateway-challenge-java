# Tom Brown - Coding Challenge Solution

This is Tom Brown's solution to the Checkout.com coding challenge.

## Requirements
- JDK 17
- Docker

## Running the application

* Run the simulator: `docker-compose up -d`
* Run the tests `./gradlew clean test`
* Run the application `./gradlew bootRun`
* Swagger is available on `http://localhost:8090/swagger-ui/index.html`

## Design Decisions
* Supported currencies are: `GBP, USD, EUR`.
* Amounts are positive integers specified in minor units.
* A card remains valid until the end of its expiry month.
* Invalid requests are rejected before the acquiring bank is called.
* Rejected requests do not persist payments.
* Authorized and Declined requests are persisted and retrievable.
* Acquiring-bank technical failures are not persisted as completed payments.
* Automatic retries are not supported because safely retrying a payment requires an idempotency mechanism and defined retry semantics with the acquiring bank.
* Full card numbers and CVVs are not persisted, only the last 4 digits of the card are persisted.
* A ConcurrentHashMap is used for in-memory storage, this is to ensure that put / get operations are thread-safe
* For storing payments we use putIfAbsent, this is to ensure that a duplicate payment ID will not overwrite an existing payment and get payment will return a consistent value.
* Storage for this implementation is deliberately in-memory

## Production Considerations
* A production system would contain the following -
  * Durable storage and sensible database isolation levels.
  * Exporting metrics for observability purposes.
  * Stable idempotency keys stored using a unique database constraint.
  * Payment reconciliation jobs