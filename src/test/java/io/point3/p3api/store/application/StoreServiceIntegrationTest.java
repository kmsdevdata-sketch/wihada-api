package io.point3.p3api.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.account.application.settlement.port.SellerSettlementAccountPersistencePort;
import io.point3.p3api.account.domain.entity.SellerSettlementAccount;
import io.point3.p3api.asset.domain.entity.Asset;
import io.point3.p3api.asset.infrastructure.persistence.AssetJpaRepository;
import io.point3.p3api.assetvariant.domain.entity.AssetVariant;
import io.point3.p3api.assetvariant.domain.type.AssetVariantType;
import io.point3.p3api.assetvariant.infrastructure.persistence.AssetVariantJpaRepository;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.AssetErrorCode;
import io.point3.p3api.exception.code.StoreErrorCode;
import io.point3.p3api.orderform.domain.entity.OrderFormTemplate;
import io.point3.p3api.orderform.infrastructure.persistence.OrderFormTemplateJpaRepository;
import io.point3.p3api.store.application.businesshours.StoreBusinessHoursService;
import io.point3.p3api.store.application.businesshours.command.UpdateStoreBusinessHoursCommand;
import io.point3.p3api.store.application.create.CreateStoreCommand;
import io.point3.p3api.store.application.notice.port.StoreNoticePersistencePort;
import io.point3.p3api.store.application.representative.RepresentativeImageService;
import io.point3.p3api.store.application.representative.command.CreateRepresentativeImageCommand;
import io.point3.p3api.store.application.representative.command.UpdateRepresentativeImageCommand;
import io.point3.p3api.store.application.representative.result.RepresentativeImageResult;
import io.point3.p3api.store.application.result.StoreResult;
import io.point3.p3api.store.application.setting.StoreSettingService;
import io.point3.p3api.store.application.setting.command.UpdateStoreSettingCommand;
import io.point3.p3api.store.application.update.ChangeStoreStatusCommand;
import io.point3.p3api.store.application.update.UpdateStoreCommand;
import io.point3.p3api.store.application.update.UpdateStoreDescriptionCommand;
import io.point3.p3api.store.domain.entity.Store;
import io.point3.p3api.store.domain.entity.StoreNotice;
import io.point3.p3api.store.domain.entity.StoreOperationSetting;
import io.point3.p3api.store.domain.entity.StoreWeeklyPickupSetting;
import io.point3.p3api.store.domain.type.StoreNoticeType;
import io.point3.p3api.store.domain.type.StoreRepresentativeImageStatus;
import io.point3.p3api.store.domain.type.StoreStatus;
import io.point3.p3api.store.infrastructure.persistence.StoreJpaRepository;
import io.point3.p3api.store.infrastructure.persistence.StoreOperationSettingJpaRepository;
import io.point3.p3api.store.infrastructure.persistence.StoreWeeklyPickupSettingJpaRepository;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "p3.asset.delivery.base-url=https://assets.example.test")
class StoreServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private StoreService storeService;

  @Autowired
  private StoreBusinessHoursService storeBusinessHoursService;

  @Autowired
  private StoreSettingService storeSettingService;

  @Autowired
  private RepresentativeImageService representativeImageService;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private StoreJpaRepository storeJpaRepository;

  @Autowired
  private AssetJpaRepository assetJpaRepository;

  @Autowired
  private AssetVariantJpaRepository assetVariantJpaRepository;

  @Autowired
  private OrderFormTemplateJpaRepository orderFormTemplateJpaRepository;

  @Autowired
  private StoreWeeklyPickupSettingJpaRepository storeWeeklyPickupSettingJpaRepository;

  @Autowired
  private StoreOperationSettingJpaRepository storeOperationSettingJpaRepository;

  @Autowired
  private StoreNoticePersistencePort storeNoticePersistencePort;

  @Autowired
  private SellerSettlementAccountPersistencePort sellerSettlementAccountPersistencePort;

  @Test
  @DisplayName("스토어 생성은 실제 저장소에서 판매자 1명당 1개 제약을 검증한다")
  void rejectsDuplicateStoreForSameOwner() {
    User seller = saveSeller();
    storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> storeService.create(createStoreCommand(seller.getId(), "다른 베이커리")));

    assertEquals(StoreErrorCode.STORE_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  @DisplayName("스토어 생성은 기본 운영 설정을 함께 저장한다")
  void createsDefaultOperationSetting() {
    User seller = saveSeller();

    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));

    StoreOperationSetting setting =
        storeOperationSettingJpaRepository.findById(store.id()).orElseThrow();
    assertEquals(2880, setting.getLeadTimeMinutes());
    assertEquals(2, setting.getCancellationCutoffDays());
  }

  @Test
  @DisplayName("매장 소개 전용 수정은 다른 스토어 기본 정보를 유지한다")
  void updatesStoreDescriptionOnly() {
    User seller = saveSeller();
    StoreResult created = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));

    StoreResult updated = storeService.updateDescription(
        new UpdateStoreDescriptionCommand(created.id(), "새로운 매장 소개"));
    Store persisted = storeJpaRepository.findById(created.id()).orElseThrow();

    assertEquals("새로운 매장 소개", updated.description());
    assertEquals("새로운 매장 소개", persisted.getDescription());
    assertEquals(created.name(), persisted.getName());
    assertEquals(created.address(), persisted.getAddress());
  }

  @Test
  @DisplayName("스토어 수정 후에도 입점 승인 시 설정한 위치를 유지한다")
  void preservesStoreLocationOnUpdate() {
    User seller = saveSeller();
    StoreResult created = storeService.create(createStoreCommandWithDetailAddress(seller.getId()));

    StoreResult updated = storeService.update(updateStoreCommand(created.id(), null));
    Store persisted = storeJpaRepository.findById(created.id()).orElseThrow();

    assertEquals("서울특별시 중구 101호", updated.address());
    assertEquals("서울특별시 중구", persisted.getAddress());
    assertEquals("101호", persisted.getDetailAddress());
  }

  @Test
  @DisplayName("스토어 생성은 프로필 Asset 소유권을 검증한다")
  void rejectsProfileAssetOwnedByAnotherUserOnCreate() {
    User seller = saveSeller();
    User anotherSeller = saveSeller();
    Asset profileAsset = saveAsset(anotherSeller.getId(), 0);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> storeService.create(
            createStoreCommand(seller.getId(), "P3 베이커리", profileAsset.getId())));

    assertEquals(StoreErrorCode.PROFILE_ASSET_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  @DisplayName("스토어 수정은 프로필 Asset 소유권을 검증한다")
  void rejectsProfileAssetOwnedByAnotherUserOnUpdate() {
    User seller = saveSeller();
    User anotherSeller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    Asset profileAsset = saveAsset(anotherSeller.getId(), 0);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> storeService.update(updateStoreCommand(store.id(), profileAsset.getId())));

    assertEquals(StoreErrorCode.PROFILE_ASSET_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  @DisplayName("processed variant가 없으면 대표 이미지를 생성할 수 없다")
  void rejectsRepresentativeImageWithoutProcessedVariant() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    Asset asset = saveAsset(seller.getId(), 0);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> representativeImageService.create(
            new CreateRepresentativeImageCommand(store.id(), asset.getId(), 0)));

    assertEquals(AssetErrorCode.ASSET_VARIANT_NOT_READY, exception.getErrorCode());
  }

  @Test
  @DisplayName("대표 이미지 응답은 processed variant delivery URL을 포함한다")
  void createsRepresentativeImageWithDeliveryUrl() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    Asset asset = saveAsset(seller.getId(), 0);
    AssetVariant variant = saveVariant(asset, 0);

    RepresentativeImageResult result = representativeImageService.create(
        new CreateRepresentativeImageCommand(store.id(), asset.getId(), 0));

    String deliveryUrl = "https://assets.example.test/" + variant.getObjectKey();
    assertEquals(1, result.variants().size());
    assertEquals(deliveryUrl, result.deliveryUrl());
    assertEquals("MEDIUM", result.variants().getFirst().type());
    assertEquals(deliveryUrl, result.variants().getFirst().deliveryUrl());
  }

  @Test
  @DisplayName("대표이미지가 3개 미만이면 스토어 활성화를 거절한다")
  void rejectsActivationWhenRepresentativeImagesAreLessThanThree() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    createRepresentativeImage(store.id(), seller.getId(), 0);
    createRepresentativeImage(store.id(), seller.getId(), 1);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> storeService.changeStatus(
            new ChangeStoreStatusCommand(store.id(), StoreStatus.ACTIVE)));

    assertEquals(StoreErrorCode.REPRESENTATIVE_IMAGE_MINIMUM_REQUIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("활성 주문서가 없으면 스토어 활성화를 거절한다")
  void rejectsActivationWhenActiveOrderFormIsMissing() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    createRepresentativeImage(store.id(), seller.getId(), 0);
    createRepresentativeImage(store.id(), seller.getId(), 1);
    createRepresentativeImage(store.id(), seller.getId(), 2);
    prepareStoreInfoAndSettlementAccount(store.id());

    BaseException exception = assertThrows(
        BaseException.class,
        () -> storeService.changeStatus(
            new ChangeStoreStatusCommand(store.id(), StoreStatus.ACTIVE)));

    assertEquals(StoreErrorCode.ACTIVE_ORDER_FORM_REQUIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("대표이미지가 3개 이상이면 스토어를 활성화한다")
  void activatesStoreWhenRepresentativeImagesAreReady() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    createRepresentativeImage(store.id(), seller.getId(), 0);
    createRepresentativeImage(store.id(), seller.getId(), 1);
    createRepresentativeImage(store.id(), seller.getId(), 2);
    prepareForActivation(store.id());

    StoreResult activated =
        storeService.changeStatus(new ChangeStoreStatusCommand(store.id(), StoreStatus.ACTIVE));

    Store persisted = storeJpaRepository.findById(store.id()).orElseThrow();
    assertEquals(StoreStatus.ACTIVE, activated.status());
    assertEquals(StoreStatus.ACTIVE, persisted.getStatus());
  }

  @Test
  @DisplayName("판매자 스토어 조회는 주간 픽업 설정에서 영업시간 표시 문자열을 파생한다")
  void derivesBusinessHoursFromWeeklyPickupSettings() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    saveWeeklyPickupSettings(store.id());

    StoreResult result = storeService.getStore(store.id());

    assertEquals("화~일 9:00~20:00 · 월 휴무 · 휴게시간 12:00~13:00", result.businessHours());
  }

  @Test
  @DisplayName("영업시간 수정은 주간 픽업 설정과 스토어 영업시간 문자열을 함께 갱신한다")
  void updatesStoreBusinessHoursWithWeeklyPickupSettings() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));

    storeBusinessHoursService.updateBusinessHours(new UpdateStoreBusinessHoursCommand(
        store.id(),
        List.of(
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY),
        LocalTime.of(9, 0),
        LocalTime.of(20, 0),
        LocalTime.of(12, 0),
        LocalTime.of(13, 0)));

    Store persisted = storeJpaRepository.findById(store.id()).orElseThrow();

    assertEquals("화~일 9:00~20:00 · 월 휴무 · 휴게시간 12:00~13:00", persisted.getBusinessHours());
  }

  @Test
  @DisplayName("스토어 설정 수정은 영업시간 캐시를 갱신한다")
  void updatesBusinessHoursWhenStoreSettingsChange() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));

    storeSettingService.update(new UpdateStoreSettingCommand(
        store.id(),
        60,
        0,
        List.of(
            weeklyPickupSetting(DayOfWeek.MONDAY, false),
            weeklyPickupSetting(DayOfWeek.TUESDAY, true),
            weeklyPickupSetting(DayOfWeek.WEDNESDAY, true),
            weeklyPickupSetting(DayOfWeek.THURSDAY, true),
            weeklyPickupSetting(DayOfWeek.FRIDAY, true),
            weeklyPickupSetting(DayOfWeek.SATURDAY, true),
            weeklyPickupSetting(DayOfWeek.SUNDAY, true)),
        List.of()));

    Store persisted = storeJpaRepository.findById(store.id()).orElseThrow();

    assertEquals("화~일 9:00~20:00 · 월 휴무 · 휴게시간 12:00~13:00", persisted.getBusinessHours());
  }

  @Test
  @DisplayName("활성 스토어는 대표이미지가 3개 아래로 줄어드는 숨김을 거절한다")
  void rejectsHidingRepresentativeImageBelowMinimumForActiveStore() {
    User seller = saveSeller();
    StoreResult store = storeService.create(createStoreCommand(seller.getId(), "P3 베이커리"));
    RepresentativeImageResult first = createRepresentativeImage(store.id(), seller.getId(), 0);
    createRepresentativeImage(store.id(), seller.getId(), 1);
    createRepresentativeImage(store.id(), seller.getId(), 2);
    prepareForActivation(store.id());
    storeService.changeStatus(new ChangeStoreStatusCommand(store.id(), StoreStatus.ACTIVE));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> representativeImageService.update(new UpdateRepresentativeImageCommand(
            store.id(), first.id(), 0, StoreRepresentativeImageStatus.HIDDEN)));

    assertEquals(StoreErrorCode.REPRESENTATIVE_IMAGE_MINIMUM_REQUIRED, exception.getErrorCode());
  }

  private User saveSeller() {
    return userJpaRepository.saveAndFlush(User.create(
        UUID.randomUUID().toString(),
        uniqueEmail("seller"),
        "판매자",
        UserRole.SELLER,
        "010-0000-0000",
        SignupProvider.GOOGLE));
  }

  private CreateStoreCommand createStoreCommand(UUID ownerUserId, String name) {
    return createStoreCommand(ownerUserId, name, null);
  }

  private CreateStoreCommand createStoreCommand(
      UUID ownerUserId, String name, UUID profileAssetId) {
    return new CreateStoreCommand(
        ownerUserId,
        name,
        profileAssetId,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        "{\"instagram\":\"https://instagram.com/p3bakery\"}",
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구");
  }

  private UpdateStoreCommand updateStoreCommand(UUID storeId, UUID profileAssetId) {
    return new UpdateStoreCommand(
        storeId,
        "P3 베이커리",
        profileAssetId,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        "{\"instagram\":\"https://instagram.com/p3bakery\"}",
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}");
  }

  private CreateStoreCommand createStoreCommandWithDetailAddress(UUID ownerUserId) {
    return new CreateStoreCommand(
        ownerUserId,
        "P3 베이커리",
        null,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        "{\"instagram\":\"https://instagram.com/p3bakery\"}",
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구",
        "101호");
  }

  private RepresentativeImageResult createRepresentativeImage(
      UUID storeId, UUID uploadedBy, int sortOrder) {
    Asset asset = saveAsset(uploadedBy, sortOrder);
    saveVariant(asset, sortOrder);

    return representativeImageService.create(
        new CreateRepresentativeImageCommand(storeId, asset.getId(), sortOrder));
  }

  private Asset saveAsset(UUID uploadedBy, int sortOrder) {
    return assetJpaRepository.saveAndFlush(Asset.create(
        UUID.randomUUID(),
        uploadedBy,
        "cake-" + sortOrder + ".png",
        "image/png",
        1024,
        "original/" + UUID.randomUUID() + "/cake-" + sortOrder + ".png"));
  }

  private AssetVariant saveVariant(Asset asset, int sortOrder) {
    return assetVariantJpaRepository.saveAndFlush(AssetVariant.create(
        asset,
        AssetVariantType.MEDIUM,
        "processed/" + UUID.randomUUID() + "/cake-" + sortOrder + ".webp",
        "image/webp",
        640,
        640,
        512));
  }

  private void prepareForActivation(UUID storeId) {
    orderFormTemplateJpaRepository.saveAndFlush(OrderFormTemplate.create(storeId, "기본 주문서"));
    storeNoticePersistencePort.replaceAllByStoreId(
        storeId,
        Arrays.stream(StoreNoticeType.values())
            .map(type -> StoreNotice.create(storeId, type, "안내"))
            .toList());
    prepareStoreInfoAndSettlementAccount(storeId);
  }

  private void prepareStoreInfoAndSettlementAccount(UUID storeId) {
    storeWeeklyPickupSettingJpaRepository.saveAndFlush(StoreWeeklyPickupSetting.create(
        storeId, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), true));
    Store store = storeJpaRepository.findById(storeId).orElseThrow();
    store.updateCancellationRefundPolicy("픽업 7일 전 100% 환불");
    storeJpaRepository.saveAndFlush(store);
    sellerSettlementAccountPersistencePort.save(SellerSettlementAccount.createRegisteredBusinessAccount(
        storeId,
        "004",
        "encrypted-account-number",
        "encrypted-account-holder",
        "encrypted-business-registration-number"));
  }

  private void saveWeeklyPickupSettings(UUID storeId) {
    storeWeeklyPickupSettingJpaRepository.saveAllAndFlush(List.of(
        weeklyPickupSetting(storeId, DayOfWeek.MONDAY, false),
        weeklyPickupSetting(storeId, DayOfWeek.TUESDAY, true),
        weeklyPickupSetting(storeId, DayOfWeek.WEDNESDAY, true),
        weeklyPickupSetting(storeId, DayOfWeek.THURSDAY, true),
        weeklyPickupSetting(storeId, DayOfWeek.FRIDAY, true),
        weeklyPickupSetting(storeId, DayOfWeek.SATURDAY, true),
        weeklyPickupSetting(storeId, DayOfWeek.SUNDAY, true)));
  }

  private StoreWeeklyPickupSetting weeklyPickupSetting(
      UUID storeId, DayOfWeek dayOfWeek, boolean enabled) {
    return StoreWeeklyPickupSetting.create(
        storeId,
        dayOfWeek,
        LocalTime.of(9, 0),
        LocalTime.of(20, 0),
        LocalTime.of(12, 0),
        LocalTime.of(13, 0),
        enabled);
  }

  private UpdateStoreSettingCommand.WeeklyPickupSetting weeklyPickupSetting(
      DayOfWeek dayOfWeek, boolean enabled) {
    return new UpdateStoreSettingCommand.WeeklyPickupSetting(
        dayOfWeek,
        LocalTime.of(9, 0),
        LocalTime.of(20, 0),
        enabled,
        LocalTime.of(12, 0),
        LocalTime.of(13, 0));
  }
}
