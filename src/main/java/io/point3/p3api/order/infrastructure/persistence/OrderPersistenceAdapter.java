package io.point3.p3api.order.infrastructure.persistence;

import io.point3.p3api.order.application.port.OrderPersistencePort;
import io.point3.p3api.order.application.query.order.OrderListDateBasis;
import io.point3.p3api.order.application.query.order.SellerOrderListQuery;
import io.point3.p3api.order.application.result.OrderPickupDateCount;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.domain.type.PaymentAttemptStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderPersistencePort {

  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final OrderJpaRepository orderJpaRepository;

  @Override
  public Order save(Order order) {
    return orderJpaRepository.save(order);
  }

  @Override
  public Optional<Order> findById(UUID orderId) {
    return orderJpaRepository.findById(orderId);
  }

  @Override
  public Optional<Order> findByIdAndBuyerUserId(UUID orderId, UUID buyerUserId) {
    return orderJpaRepository.findByIdAndBuyerUserId(orderId, buyerUserId);
  }

  @Override
  public Optional<Order> findByIdAndStoreId(UUID orderId, UUID storeId) {
    return orderJpaRepository.findByIdAndStoreId(orderId, storeId);
  }

  @Override
  public Optional<Order> findByIdAndStoreIdForUpdate(UUID orderId, UUID storeId) {
    return orderJpaRepository.findByIdAndStoreIdForUpdate(orderId, storeId);
  }

  @Override
  public Optional<Order> findByPaymentAttemptId(UUID paymentAttemptId) {
    return orderJpaRepository.findByPaymentAttemptId(paymentAttemptId);
  }

  @Override
  public List<Order> findAllByBuyerUserId(UUID buyerUserId) {
    return orderJpaRepository.findAllByBuyerUserIdOrderByCreatedAtDesc(buyerUserId);
  }

  @Override
  public List<Order> findAllByStoreId(UUID storeId) {
    return orderJpaRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId);
  }

  @Override
  public List<Order> findAllByIds(List<UUID> orderIds) {
    return orderJpaRepository.findAllById(orderIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Order> findSellerOrders(
      SellerOrderListQuery query, Instant startInclusive, Instant endExclusive) {
    Specification<Order> specification = Specification.allOf(
        storeEquals(query.storeId()),
        statusIn(query.statuses()),
        dateBetween(query.dateBasis(), startInclusive, endExclusive));
    return orderJpaRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Override
  public List<OrderPickupDateCount> countByStoreIdAndPickupAtBetween(
      UUID storeId, Instant fromInclusive, Instant toExclusive, Set<OrderStatus> statuses) {

    Map<LocalDate, Long> counts =
        orderJpaRepository
            .countByStoreIdAndPickupAtBetween(storeId, fromInclusive, toExclusive, statuses)
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(
                count -> count.getPickupAt().atZone(KOREA_ZONE_ID).toLocalDate(),
                java.util.stream.Collectors.summingLong(
                    OrderJpaRepository.PickupAtOrderCount::getOrderCount)));

    return counts.entrySet().stream()
        .map(entry -> new OrderPickupDateCount(entry.getKey(), entry.getValue()))
        .sorted(java.util.Comparator.comparing(OrderPickupDateCount::pickupDate))
        .toList();
  }

  @Override
  public List<Order> findCalendarOrders(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    return orderJpaRepository.findCalendarOrders(storeId, startInclusive, endExclusive);
  }

  @Override
  public List<Order> findCalendarOrdersByStatus(
      UUID storeId, OrderStatus status, Instant startInclusive, Instant endExclusive) {
    return orderJpaRepository.findCalendarOrdersByStatus(
        storeId, status, startInclusive, endExclusive);
  }

  @Override
  public long sumSucceededPaymentAmount(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    return orderJpaRepository.sumSucceededPaymentAmount(
        storeId, PaymentAttemptStatus.SUCCEEDED, startInclusive, endExclusive);
  }

  @Override
  public long countSucceededPayments(UUID storeId, Instant startInclusive, Instant endExclusive) {
    return orderJpaRepository.countSucceededPayments(
        storeId, PaymentAttemptStatus.SUCCEEDED, startInclusive, endExclusive);
  }

  @Override
  public List<Order> findSucceededPaymentOrders(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    return orderJpaRepository.findSucceededPaymentOrders(
        storeId, PaymentAttemptStatus.SUCCEEDED, startInclusive, endExclusive);
  }

  @Override
  public long countByStoreIdAndStatus(UUID storeId, OrderStatus status) {
    return orderJpaRepository.countByStoreIdAndStatus(storeId, status);
  }

  @Override
  public long countByStoreIdAndStatuses(UUID storeId, Collection<OrderStatus> statuses) {
    return orderJpaRepository.countByStoreIdAndStatusIn(storeId, statuses);
  }

  private Specification<Order> storeEquals(UUID storeId) {
    return (root, query, cb) -> cb.equal(root.get("storeId"), storeId);
  }

  private Specification<Order> statusIn(Set<OrderStatus> statuses) {
    return (root, query, cb) -> {
      if (statuses.isEmpty()) {
        return cb.conjunction();
      }
      return root.get("status").in(statuses);
    };
  }

  private Specification<Order> dateBetween(
      OrderListDateBasis dateBasis, Instant startInclusive, Instant endExclusive) {
    if (dateBasis == OrderListDateBasis.PAID_AT) {
      return paidAtBetween(startInclusive, endExclusive);
    }
    if (dateBasis == OrderListDateBasis.PICKUP_AT) {
      return dateFieldBetween("pickupAt", startInclusive, endExclusive);
    }
    return dateFieldBetween("createdAt", startInclusive, endExclusive);
  }

  private Specification<Order> dateFieldBetween(
      String field, Instant startInclusive, Instant endExclusive) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (startInclusive != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get(field), startInclusive));
      }
      if (endExclusive != null) {
        predicates.add(cb.lessThan(root.get(field), endExclusive));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private Specification<Order> paidAtBetween(Instant startInclusive, Instant endExclusive) {
    return (root, query, cb) -> {
      if (startInclusive == null && endExclusive == null) {
        return cb.conjunction();
      }

      Subquery<UUID> subquery = query.subquery(UUID.class);
      Root<PaymentAttempt> paymentAttempt = subquery.from(PaymentAttempt.class);
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(paymentAttempt.get("id"), root.get("paymentAttemptId")));
      if (startInclusive != null) {
        predicates.add(cb.greaterThanOrEqualTo(paymentAttempt.get("completedAt"), startInclusive));
      }
      if (endExclusive != null) {
        predicates.add(cb.lessThan(paymentAttempt.get("completedAt"), endExclusive));
      }
      subquery.select(paymentAttempt.get("id")).where(predicates.toArray(Predicate[]::new));
      return cb.exists(subquery);
    };
  }
}
