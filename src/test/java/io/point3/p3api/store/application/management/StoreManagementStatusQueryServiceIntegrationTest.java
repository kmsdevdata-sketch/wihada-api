package io.point3.p3api.store.application.management;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.account.application.settlement.port.SellerSettlementAccountPersistencePort;
import io.point3.p3api.account.domain.entity.SellerSettlementAccount;
import io.point3.p3api.asset.domain.entity.Asset;
import io.point3.p3api.asset.infrastructure.persistence.AssetJpaRepository;
import io.point3.p3api.store.application.StoreService;
import io.point3.p3api.store.application.create.CreateStoreCommand;
import io.point3.p3api.store.application.notice.port.StoreNoticePersistencePort;
import io.point3.p3api.store.application.refundpolicy.command.UpdateStoreRefundPolicyCommand;
import io.point3.p3api.store.application.refundpolicy.update.StoreRefundPolicyUpdateUseCase;
import io.point3.p3api.store.application.representative.port.RepresentativeImagePersistencePort;
import io.point3.p3api.store.application.result.StoreResult;
import io.point3.p3api.store.application.setting.port.StoreWeeklyPickupSettingPersistencePort;
import io.point3.p3api.store.domain.entity.StoreNotice;
import io.point3.p3api.store.domain.entity.StoreRepresentativeImage;
import io.point3.p3api.store.domain.entity.StoreWeeklyPickupSetting;
import io.point3.p3api.store.domain.type.StoreNoticeType;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoreManagementStatusQueryServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private StoreManagementStatusQueryService storeManagementStatusQueryService;

  @Autowired
  private StoreNoticePersistencePort storeNoticePersistencePort;

  @Autowired
  private RepresentativeImagePersistencePort representativeImagePersistencePort;

  @Autowired
  private StoreService storeService;

  @Autowired
  private StoreRefundPolicyUpdateUseCase storeRefundPolicyUpdateUseCase;

  @Autowired
  private StoreWeeklyPickupSettingPersistencePort weeklyPickupSettingPersistencePort;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private AssetJpaRepository assetJpaRepository;

  @Autowired
  private SellerSettlementAccountPersistencePort sellerSettlementAccountPersistencePort;

  @Test
  @DisplayName("5개 공지가 모두 비공백일 때만 스토어 관리 공지 항목을 완료로 판단한다")
  void completesNoticeOnlyWhenAllNoticesHaveContent() {
    StoreResult store = createStore();
    storeNoticePersistencePort.replaceAllByStoreId(
        store.id(),
        List.of(
            notice(store.id(), StoreNoticeType.PICKUP_DELIVERY, "픽업 안내"),
            notice(store.id(), StoreNoticeType.DESIGN_PRODUCTION, "디자인 안내"),
            notice(store.id(), StoreNoticeType.PAYMENT, "결제 안내"),
            notice(store.id(), StoreNoticeType.CAKE_CARE, "보관 안내"),
            notice(store.id(), StoreNoticeType.BUSINESS_HOURS, " ")));

    boolean blankNotice =
        storeManagementStatusQueryService.getStatus(store.id()).items().notice();

    storeNoticePersistencePort.replaceAllByStoreId(
        store.id(),
        List.of(
            notice(store.id(), StoreNoticeType.PICKUP_DELIVERY, "픽업 안내"),
            notice(store.id(), StoreNoticeType.DESIGN_PRODUCTION, "디자인 안내"),
            notice(store.id(), StoreNoticeType.PAYMENT, "결제 안내"),
            notice(store.id(), StoreNoticeType.CAKE_CARE, "보관 안내"),
            notice(store.id(), StoreNoticeType.BUSINESS_HOURS, "영업시간 안내")));

    boolean completedNotice =
        storeManagementStatusQueryService.getStatus(store.id()).items().notice();

    assertFalse(blankNotice);
    assertTrue(completedNotice);
  }

  @Test
  @DisplayName("갤러리 이미지가 없어도 대표사진 3장 이상이면 사진 등록을 완료로 판단한다")
  void completesPhotoRegistrationWithoutGalleryImage() {
    StoreResult store = createStore();
    for (int sortOrder = 0; sortOrder < 3; sortOrder++) {
      Asset asset = assetJpaRepository.saveAndFlush(Asset.create(
          UUID.randomUUID(),
          store.ownerUserId(),
          "representative-" + sortOrder + ".png",
          "image/png",
          1024,
          "representative/" + UUID.randomUUID() + ".png"));
      representativeImagePersistencePort.save(
          StoreRepresentativeImage.create(store.id(), asset.getId(), sortOrder));
    }

    var status = storeManagementStatusQueryService.getStatus(store.id());

    assertTrue(status.items().photoRegistration());
    assertFalse(status.activationBlockedReasons().contains("GALLERY_IMAGE_REQUIRED"));
  }

  @Test
  @DisplayName("환불정책 저장 뒤 주소와 활성 픽업 설정이 있으면 스토어 정보를 완료로 판단한다")
  void completesStoreInfoAfterSavingRefundPolicy() {
    StoreResult store = createStore();
    weeklyPickupSettingPersistencePort.saveAll(List.of(StoreWeeklyPickupSetting.create(
        store.id(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), true)));

    assertFalse(storeManagementStatusQueryService.getStatus(store.id()).items().storeInfo());

    storeRefundPolicyUpdateUseCase.updateRefundPolicy(new UpdateStoreRefundPolicyCommand(
        store.id(),
        List.of(
            new UpdateStoreRefundPolicyCommand.Rule(7, 100),
            new UpdateStoreRefundPolicyCommand.Rule(5, 80))));

    assertTrue(storeManagementStatusQueryService.getStatus(store.id()).items().storeInfo());
  }

  @Test
  @DisplayName("매장 소개가 없으면 환불정책과 활성 픽업 설정이 있어도 스토어 정보를 완료로 판단하지 않는다")
  void doesNotCompleteStoreInfoWithoutDescription() {
    StoreResult store = createStore(null);
    weeklyPickupSettingPersistencePort.saveAll(List.of(StoreWeeklyPickupSetting.create(
        store.id(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), true)));
    storeRefundPolicyUpdateUseCase.updateRefundPolicy(new UpdateStoreRefundPolicyCommand(
        store.id(), List.of(new UpdateStoreRefundPolicyCommand.Rule(7, 100))));

    var status = storeManagementStatusQueryService.getStatus(store.id());

    assertFalse(status.items().storeInfo());
    assertFalse(status.canActivate());
    assertTrue(status.activationBlockedReasons().contains("STORE_INFORMATION_REQUIRED"));
  }

  @Test
  @DisplayName("정산계좌가 등록된 경우 정산계좌 항목을 완료로 판단한다")
  void completesSettlementAccountWithRegisteredAccount() {
    StoreResult store = createStore();

    assertFalse(
        storeManagementStatusQueryService.getStatus(store.id()).items().settlementAccount());

    sellerSettlementAccountPersistencePort.save(SellerSettlementAccount.createRegisteredBusinessAccount(
        store.id(),
        "004",
        "encrypted-account-number",
        "encrypted-account-holder",
        "encrypted-business-registration-number"));

    assertTrue(storeManagementStatusQueryService.getStatus(store.id()).items().settlementAccount());
  }

  private StoreNotice notice(UUID storeId, StoreNoticeType type, String content) {
    return StoreNotice.create(storeId, type, content);
  }

  private StoreResult createStore() {
    return createStore("주문제작 케이크 스토어");
  }

  private StoreResult createStore(String description) {
    User seller = userJpaRepository.saveAndFlush(User.create(
        UUID.randomUUID().toString(),
        uniqueEmail("store-management-seller"),
        "판매자",
        UserRole.SELLER,
        "010-0000-0000",
        SignupProvider.GOOGLE));
    return storeService.create(new CreateStoreCommand(
        seller.getId(),
        "P3 베이커리",
        null,
        description,
        "010-1234-5678",
        true,
        null,
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구"));
  }
}
