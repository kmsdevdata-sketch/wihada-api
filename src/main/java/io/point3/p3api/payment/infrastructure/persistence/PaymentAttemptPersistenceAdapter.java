package io.point3.p3api.payment.infrastructure.persistence;

import io.point3.p3api.payment.application.port.PaymentAttemptPersistencePort;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class PaymentAttemptPersistenceAdapter implements PaymentAttemptPersistencePort {
  private final PaymentAttemptJpaRepository paymentAttemptJpaRepository;

  @Override
  public PaymentAttempt save(PaymentAttempt paymentAttempt) {
    return paymentAttemptJpaRepository.save(paymentAttempt);
  }

  @Override
  public Optional<PaymentAttempt> findById(UUID paymentAttemptId) {
    return paymentAttemptJpaRepository.findById(paymentAttemptId);
  }

  @Override
  public Optional<PaymentAttempt> findByIdForUpdate(UUID paymentAttemptId) {
    return paymentAttemptJpaRepository.findByIdForUpdate(paymentAttemptId);
  }

  @Override
  public Optional<PaymentAttempt> findByPoint3SessionId(String point3SessionId) {
    return paymentAttemptJpaRepository.findByPoint3SessionId(point3SessionId);
  }

  @Override
  public List<PaymentAttempt> findAllById(List<UUID> paymentAttemptIds) {
    return paymentAttemptJpaRepository.findAllById(paymentAttemptIds);
  }

  @Override
  public List<PaymentAttempt> findAllByConfirmationId(UUID confirmationId) {
    return paymentAttemptJpaRepository.findAllByConfirmationIdOrderByCreatedAtDesc(confirmationId);
  }
}
