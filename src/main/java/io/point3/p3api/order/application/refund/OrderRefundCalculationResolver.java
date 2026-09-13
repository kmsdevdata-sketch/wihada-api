package io.point3.p3api.order.application.refund;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.store.application.refundpolicy.port.StoreRefundPolicyPersistencePort;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRefundCalculationResolver {

  private final OrderRefundPolicyCalculator orderRefundPolicyCalculator;
  private final StoreRefundPolicyPersistencePort storeRefundPolicyPersistencePort;

  public OrderRefundCalculation resolve(Order order, Instant processingAt) {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(processingAt, "processingAt");

    CalculationBase calculationBase = calculationBase(order, processingAt);
    var calculation = orderRefundPolicyCalculator.calculate(
        order.getPaidAmount(),
        order.getPickupAt(),
        calculationBase.calculationBaseAt(),
        storeRefundPolicyPersistencePort.findAllByStoreId(order.getStoreId()));

    return new OrderRefundCalculation(
        order.getPaidAmount(),
        calculation.amount(),
        calculation.refundRate(),
        calculationBase.calculationBaseAt(),
        calculationBase.calculationBasis());
  }

  private CalculationBase calculationBase(Order order, Instant processingAt) {
    if (order.getStatus() == OrderStatus.REFUND_REQUESTED) {
      return new CalculationBase(
          requireRefundRequestedAt(order), OrderRefundCalculationBasis.REFUND_REQUESTED_AT);
    }
    if (order.getStatus() == OrderStatus.PAID) {
      return new CalculationBase(processingAt, OrderRefundCalculationBasis.CURRENT_TIME);
    }
    throw new BaseException(OrderErrorCode.ORDER_STATUS_FORBIDDEN);
  }

  private Instant requireRefundRequestedAt(Order order) {
    Instant refundRequestedAt = order.getRefundRequestedAt();
    if (refundRequestedAt == null) {
      throw new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
    return refundRequestedAt;
  }

  private record CalculationBase(
      Instant calculationBaseAt, OrderRefundCalculationBasis calculationBasis) {}
}
