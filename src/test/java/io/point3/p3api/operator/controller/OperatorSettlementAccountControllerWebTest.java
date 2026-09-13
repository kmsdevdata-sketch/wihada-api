package io.point3.p3api.operator.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.point3.p3api.account.application.settlement.OperatorSettlementAccountQueryUseCase;
import io.point3.p3api.account.application.settlement.OperatorSettlementAccountResult;
import io.point3.p3api.auth.infrastructure.web.Authenticated;
import io.point3.p3api.auth.infrastructure.web.CurrentUser;
import io.point3.p3api.common.web.response.GlobalExceptionHandler;
import io.point3.p3api.operator.application.OperatorManagementUseCase;
import io.point3.p3api.user.domain.type.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class OperatorSettlementAccountControllerWebTest {

  private final OperatorManagementUseCase operatorManagementUseCase =
      mock(OperatorManagementUseCase.class);
  private final OperatorSettlementAccountQueryUseCase settlementAccountQueryUseCase =
      mock(OperatorSettlementAccountQueryUseCase.class);

  private MockMvc mockMvc;
  private CurrentUser currentUser;

  @BeforeEach
  void setUp() {
    currentUser = new CurrentUser(UUID.randomUUID(), "운영자", UserRole.OPERATOR);
    OperatorManagementController controller = new OperatorManagementController(
        operatorManagementUseCase, settlementAccountQueryUseCase);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
        .build();
  }

  @Test
  @DisplayName("운영자는 스토어 정산계좌 원문을 조회할 수 있다")
  void getsSettlementAccount() throws Exception {
    UUID storeId = UUID.randomUUID();
    when(settlementAccountQueryUseCase.getForOperator(storeId))
        .thenReturn(new OperatorSettlementAccountResult(
            "090", "카카오뱅크", "홍길동", "1234567890123", "******7890"));

    mockMvc
        .perform(get("/operator/stores/{storeId}/settlement-account", storeId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.bankCode").value("090"))
        .andExpect(jsonPath("$.data.bankName").value("카카오뱅크"))
        .andExpect(jsonPath("$.data.accountHolderName").value("홍길동"))
        .andExpect(jsonPath("$.data.accountNumber").value("1234567890123"))
        .andExpect(jsonPath("$.data.businessRegistrationNumberMasked").value("******7890"));

    verify(settlementAccountQueryUseCase).getForOperator(storeId);
  }

  @Test
  @DisplayName("운영자가 아닌 사용자는 정산계좌 원문을 조회할 수 없다")
  void rejectsSettlementAccountForNonOperator() throws Exception {
    currentUser = new CurrentUser(UUID.randomUUID(), "판매자", UserRole.SELLER);

    mockMvc
        .perform(get("/operator/stores/{storeId}/settlement-account", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  private class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(Authenticated.class)
          && parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
      return currentUser;
    }
  }
}
