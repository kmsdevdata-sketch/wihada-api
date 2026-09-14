package io.point3.p3api.payment.application.port;

import io.point3.p3api.payment.domain.entity.Refund;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundPersistencePort {
  Refund save(Refund refund);

  Optional<Refund> findById(UUID refundId);

  List<Refund> findAllByOrderId(UUID orderId);

  long sumCompletedAmount(UUID storeId, Instant startInclusive, Instant endExclusive);

  long countCompleted(UUID storeId, Instant startInclusive, Instant endExclusive);

  List<Refund> findCompletedByStoreId(UUID storeId, Instant startInclusive, Instant endExclusive);
}
