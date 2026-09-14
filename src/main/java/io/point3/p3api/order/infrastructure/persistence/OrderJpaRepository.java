package io.point3.p3api.order.infrastructure.persistence;

import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.payment.domain.type.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository
    extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
  Optional<Order> findByIdAndBuyerUserId(UUID orderId, UUID buyerUserId);

  Optional<Order> findByIdAndStoreId(UUID orderId, UUID storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select orders from Order orders where orders.id = :orderId and orders.storeId = :storeId")
  Optional<Order> findByIdAndStoreIdForUpdate(
      @Param("orderId") UUID orderId, @Param("storeId") UUID storeId);

  Optional<Order> findByPaymentAttemptId(UUID paymentAttemptId);

  List<Order> findAllByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);

  List<Order> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

  @Query("""
      select o.pickupAt as pickupAt, count(o) as orderCount
      from Order o
      where o.storeId = :storeId
        and o.pickupAt >= :fromInclusive
        and o.pickupAt < :toExclusive
        and o.status in :statuses
      group by o.pickupAt
      """)
  List<PickupAtOrderCount> countByStoreIdAndPickupAtBetween(
      @Param("storeId") UUID storeId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      @Param("statuses") Set<OrderStatus> statuses);

  interface PickupAtOrderCount {

    Instant getPickupAt();

    long getOrderCount();
  }

  long countByStoreIdAndStatus(UUID storeId, OrderStatus status);

  long countByStoreIdAndStatusIn(UUID storeId, Collection<OrderStatus> statuses);

  @Query("""
      select o
      from Order o
      where o.storeId = :storeId
        and o.pickupAt >= :startInclusive
        and o.pickupAt < :endExclusive
      order by o.pickupAt asc
      """)
  List<Order> findCalendarOrders(
      @Param("storeId") UUID storeId,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);

  @Query("""
      select o
      from Order o
      where o.storeId = :storeId
        and o.status = :status
        and o.pickupAt >= :startInclusive
        and o.pickupAt < :endExclusive
      order by o.pickupAt asc
      """)
  List<Order> findCalendarOrdersByStatus(
      @Param("storeId") UUID storeId,
      @Param("status") OrderStatus status,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);

  @Query("""
      select coalesce(sum(p.amount), 0)
      from Order o, PaymentAttempt p
      where o.paymentAttemptId = p.id
        and o.storeId = :storeId
        and p.status = :status
        and p.completedAt >= :startInclusive
        and p.completedAt < :endExclusive
      """)
  long sumSucceededPaymentAmount(
      @Param("storeId") UUID storeId,
      @Param("status") PaymentAttemptStatus status,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);

  @Query("""
      select count(p)
      from Order o, PaymentAttempt p
      where o.paymentAttemptId = p.id
        and o.storeId = :storeId
        and p.status = :status
        and p.completedAt >= :startInclusive
        and p.completedAt < :endExclusive
      """)
  long countSucceededPayments(
      @Param("storeId") UUID storeId,
      @Param("status") PaymentAttemptStatus status,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);

  @Query("""
      select o
      from Order o, PaymentAttempt p
      where o.paymentAttemptId = p.id
        and o.storeId = :storeId
        and p.status = :status
        and p.completedAt >= :startInclusive
        and p.completedAt < :endExclusive
      order by p.completedAt desc
      """)
  List<Order> findSucceededPaymentOrders(
      @Param("storeId") UUID storeId,
      @Param("status") PaymentAttemptStatus status,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);
}
