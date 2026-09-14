package io.point3.p3api.order.application.port;

import io.point3.p3api.order.application.query.order.SellerOrderListQuery;
import io.point3.p3api.order.application.result.OrderPickupDateCount;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrderPersistencePort {
  Order save(Order order);

  Optional<Order> findById(UUID orderId);

  Optional<Order> findByIdAndBuyerUserId(UUID orderId, UUID buyerUserId);

  Optional<Order> findByIdAndStoreId(UUID orderId, UUID storeId);

  Optional<Order> findByIdAndStoreIdForUpdate(UUID orderId, UUID storeId);

  Optional<Order> findByPaymentAttemptId(UUID paymentAttemptId);

  List<Order> findAllByBuyerUserId(UUID buyerUserId);

  List<Order> findAllByStoreId(UUID storeId);

  List<Order> findAllByIds(List<UUID> orderIds);

  List<Order> findSellerOrders(
      SellerOrderListQuery query, Instant startInclusive, Instant endExclusive);

  List<OrderPickupDateCount> countByStoreIdAndPickupAtBetween(
      UUID storeId, Instant fromInclusive, Instant toExclusive, Set<OrderStatus> statuses);

  List<Order> findCalendarOrders(UUID storeId, Instant startInclusive, Instant endExclusive);

  List<Order> findCalendarOrdersByStatus(
      UUID storeId, OrderStatus status, Instant startInclusive, Instant endExclusive);

  long sumSucceededPaymentAmount(UUID storeId, Instant startInclusive, Instant endExclusive);

  long countSucceededPayments(UUID storeId, Instant startInclusive, Instant endExclusive);

  List<Order> findSucceededPaymentOrders(
      UUID storeId, Instant startInclusive, Instant endExclusive);

  long countByStoreIdAndStatus(UUID storeId, OrderStatus status);

  long countByStoreIdAndStatuses(UUID storeId, Collection<OrderStatus> statuses);
}
