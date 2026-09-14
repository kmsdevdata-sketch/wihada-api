package io.point3.p3api.payment.application.port;

import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptPersistencePort {
  PaymentAttempt save(PaymentAttempt paymentAttempt);

  Optional<PaymentAttempt> findById(UUID paymentAttemptId);

  Optional<PaymentAttempt> findByIdForUpdate(UUID paymentAttemptId);

  Optional<PaymentAttempt> findByPoint3SessionId(String point3SessionId);

  List<PaymentAttempt> findAllById(List<UUID> paymentAttemptIds);

  List<PaymentAttempt> findAllByConfirmationId(UUID confirmationId);
}
