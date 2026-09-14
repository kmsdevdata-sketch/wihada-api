package io.point3.p3api.dashboard.controller;

import io.point3.p3api.auth.infrastructure.security.RoleGuard;
import io.point3.p3api.auth.infrastructure.web.Authenticated;
import io.point3.p3api.auth.infrastructure.web.CurrentUser;
import io.point3.p3api.common.tenant.web.CurrentStoreId;
import io.point3.p3api.common.web.response.ApiResponse;
import io.point3.p3api.dashboard.application.query.SellerDashboardQueryCommand;
import io.point3.p3api.dashboard.application.query.SellerDashboardQueryUseCase;
import io.point3.p3api.dashboard.application.query.SellerRevenueQueryCommand;
import io.point3.p3api.dashboard.application.query.SellerRevenueTransactionQueryCommand;
import io.point3.p3api.dashboard.application.query.SellerRevenueTransactionType;
import io.point3.p3api.dashboard.controller.response.SellerDashboardResponse;
import io.point3.p3api.dashboard.controller.response.SellerRevenueResponse;
import io.point3.p3api.dashboard.controller.response.SellerRevenueTransactionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SellerDashboardController {

  private final SellerDashboardQueryUseCase sellerDashboardQueryUseCase;

  @GetMapping("/seller/dashboard")
  public ApiResponse<SellerDashboardResponse> getSummary(
      @CurrentStoreId UUID storeId, @Authenticated CurrentUser currentUser) {
    RoleGuard.requireSeller(currentUser);

    return ApiResponse.ok(SellerDashboardResponse.from(sellerDashboardQueryUseCase.getSummary(
        SellerDashboardQueryCommand.of(storeId, currentUser.userId()))));
  }

  @GetMapping("/seller/dashboard/revenue")
  public ApiResponse<SellerRevenueResponse> getRevenue(
      @CurrentStoreId UUID storeId,
      @Authenticated CurrentUser currentUser,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    RoleGuard.requireSeller(currentUser);

    return ApiResponse.ok(SellerRevenueResponse.from(sellerDashboardQueryUseCase.getRevenue(
        SellerRevenueQueryCommand.of(storeId, startDate, endDate))));
  }

  @GetMapping("/seller/dashboard/revenue/transactions")
  public ApiResponse<List<SellerRevenueTransactionResponse>> getRevenueTransactions(
      @CurrentStoreId UUID storeId,
      @Authenticated CurrentUser currentUser,
      @RequestParam SellerRevenueTransactionType type,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    RoleGuard.requireSeller(currentUser);

    return ApiResponse.ok(sellerDashboardQueryUseCase
        .getRevenueTransactions(
            SellerRevenueTransactionQueryCommand.of(storeId, type, startDate, endDate))
        .stream()
        .map(SellerRevenueTransactionResponse::from)
        .toList());
  }
}
