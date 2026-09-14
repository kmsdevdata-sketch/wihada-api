package io.point3.p3api.dashboard.application.query;

import io.point3.p3api.dashboard.application.result.SellerDashboardResult;
import io.point3.p3api.dashboard.application.result.SellerRevenueResult;
import io.point3.p3api.dashboard.application.result.SellerRevenueTransactionResult;
import java.util.List;

public interface SellerDashboardQueryUseCase {

  SellerDashboardResult getSummary(SellerDashboardQueryCommand command);

  SellerRevenueResult getRevenue(SellerRevenueQueryCommand command);

  List<SellerRevenueTransactionResult> getRevenueTransactions(
      SellerRevenueTransactionQueryCommand command);
}
