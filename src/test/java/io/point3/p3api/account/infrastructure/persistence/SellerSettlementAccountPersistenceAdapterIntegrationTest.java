package io.point3.p3api.account.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.account.application.settlement.port.SellerSettlementAccountPersistencePort;
import io.point3.p3api.account.domain.entity.SellerSettlementAccount;
import io.point3.p3api.account.domain.type.AccountHolderType;
import io.point3.p3api.store.application.StoreService;
import io.point3.p3api.store.application.create.CreateStoreCommand;
import io.point3.p3api.store.application.result.StoreResult;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SellerSettlementAccountPersistenceAdapterIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SellerSettlementAccountPersistencePort persistencePort;

  @Autowired
  private StoreService storeService;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Test
  @DisplayName("스토어의 현재 정산계좌를 저장하고 조회한다")
  void savesAndFindsAccountByStore() {
    StoreResult store = createStore();
    SellerSettlementAccount account = account(store.id());

    SellerSettlementAccount saved = persistencePort.save(account);

    assertTrue(persistencePort.existsByStoreId(store.id()));
    assertEquals(
        saved.getId(), persistencePort.findByStoreId(store.id()).orElseThrow().getId());
  }

  @Test
  @DisplayName("저장된 정산계좌를 새로운 검증 정보로 교체한다")
  void replacesCurrentAccount() {
    StoreResult store = createStore();
    SellerSettlementAccount saved = persistencePort.save(account(store.id()));
    Instant replacedAt = Instant.parse("2026-09-11T04:00:00Z");

    saved.replaceVerifiedAccount(
        "088",
        "encrypted-account-2",
        "encrypted-holder-2",
        AccountHolderType.BUSINESS,
        "provider-transaction-2",
        replacedAt);
    persistencePort.save(saved);

    SellerSettlementAccount found = persistencePort.findByStoreId(store.id()).orElseThrow();
    assertEquals("088", found.getBankCode());
    assertEquals("encrypted-account-2", found.getEncryptedAccountNumber());
    assertEquals(replacedAt, found.getVerifiedAt());
  }

  private StoreResult createStore() {
    User seller = userJpaRepository.saveAndFlush(User.create(
        UUID.randomUUID().toString(),
        uniqueEmail("settlement-account-seller"),
        "판매자",
        UserRole.SELLER,
        "010-0000-0000",
        SignupProvider.GOOGLE));
    return storeService.create(new CreateStoreCommand(
        seller.getId(),
        "P3 베이커리",
        null,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        null,
        null,
        null,
        "서울특별시 중구",
        null));
  }

  private SellerSettlementAccount account(UUID storeId) {
    return SellerSettlementAccount.createRegisteredBusinessAccount(
        storeId,
        "004",
        "encrypted-account-1",
        "encrypted-holder-1",
        "encrypted-business-registration-number-1");
  }
}
