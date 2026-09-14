package io.point3.p3api.dashboard.application.query;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SellerRevenueTransactionQueryCommand(
    UUID storeId, SellerRevenueTransactionType type, LocalDate startDate, LocalDate endDate) {

  public SellerRevenueTransactionQueryCommand {
    Objects.requireNonNull(storeId, "storeId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");

    if (startDate.isAfter(endDate)) {
      throw new BaseException(CommonErrorCode.INVALID_INPUT);
    }
  }

  public static SellerRevenueTransactionQueryCommand of(
      UUID storeId, SellerRevenueTransactionType type, LocalDate startDate, LocalDate endDate) {
    return new SellerRevenueTransactionQueryCommand(storeId, type, startDate, endDate);
  }
}
