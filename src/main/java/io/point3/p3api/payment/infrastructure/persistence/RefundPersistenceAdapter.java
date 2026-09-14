package io.point3.p3api.payment.infrastructure.persistence;

import io.point3.p3api.payment.application.port.RefundPersistencePort;
import io.point3.p3api.payment.domain.entity.Refund;
import io.point3.p3api.payment.domain.type.RefundStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class RefundPersistenceAdapter implements RefundPersistencePort {
  private final RefundJpaRepository refundJpaRepository;

  @Override
  public Refund save(Refund refund) {
    return refundJpaRepository.save(refund);
  }

  @Override
  public Optional<Refund> findById(UUID refundId) {
    return refundJpaRepository.findById(refundId);
  }

  @Override
  public List<Refund> findAllByOrderId(UUID orderId) {
    return refundJpaRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);
  }

  @Override
  public long sumCompletedAmount(UUID storeId, Instant startInclusive, Instant endExclusive) {
    return refundJpaRepository.sumCompletedAmount(
        storeId, RefundStatus.COMPLETED, startInclusive, endExclusive);
  }

  @Override
  public long countCompleted(UUID storeId, Instant startInclusive, Instant endExclusive) {
    return refundJpaRepository.countCompleted(
        storeId, RefundStatus.COMPLETED, startInclusive, endExclusive);
  }

  @Override
  public List<Refund> findCompletedByStoreId(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    return refundJpaRepository.findCompletedByStoreId(
        storeId, RefundStatus.COMPLETED, startInclusive, endExclusive);
  }
}
