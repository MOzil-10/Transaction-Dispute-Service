package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.DisputeRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: regulatory-dispute-eligibility
 *
 * Covers the correctness properties for the transaction-category eligibility gate and the
 * guarantee that blocked submissions never persist a dispute record. Both properties are
 * exercised against the real {@link DisputeService} wired into the Spring context with an H2
 * database, isolating the category/window rules by keeping every transaction well within the
 * 60-day window (unless a test deliberately pushes it out).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class DisputeEligibilityServiceTest {

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private UserRepository userRepository;

    private Long customerId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        disputeRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        customerId = authService.register(request).customerId();
        account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
    }

    /**
     * Persist a transaction owned by the test customer with the given category and transaction
     * date. A unique reference per call avoids the duplicate-dispute guard interfering across
     * iterations.
     */
    private TransactionEntity saveTransaction(TransactionCategory category, OffsetDateTime transactionDate) {
        TransactionEntity transaction =
                TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setTransactionDate(transactionDate);
        return transactionRepository.save(transaction);
    }

    // =============================================================================================
    // Feature: regulatory-dispute-eligibility, Property 4: Category eligibility gate.
    //
    // With every other guard satisfied (owned transaction, inside the 60-day window, no duplicate),
    // a submission SUCCEEDS iff the transaction category is disputable, otherwise it is rejected
    // with BadRequestException(MSG_CATEGORY_NOT_DISPUTABLE).
    // =============================================================================================
    // **Validates: Requirements 4.1, 4.2**

    @ParameterizedTest(name = "disputable category {0} should allow submission to succeed")
    @EnumSource(value = TransactionCategory.class, names = {
            "ONLINE_RETAIL", "MOBILE_CONTRACT", "GYM_MEMBERSHIP", "LOAN", "MUNICIPAL"
    })
    @DisplayName("Property 4: disputable categories allow a dispute to be submitted")
    void submitDispute_ShouldSucceed_ForDisputableCategory(TransactionCategory category) {
        // Transaction dated now() → always inside the 60-day window, isolating the category rule.
        TransactionEntity transaction = saveTransaction(category, OffsetDateTime.now());
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);

        assertThat(category.isDisputable()).isTrue();
        assertThat(response).isNotNull();
        assertThat(response.disputeId()).isNotNull();
        assertThat(response.disputeReference()).startsWith("DSP-");
    }

    @ParameterizedTest(name = "non-disputable category {0} should be rejected with 400")
    @EnumSource(value = TransactionCategory.class, names = {
            "P2P_TRANSFER", "IN_STORE_RETAIL"
    })
    @DisplayName("Property 4: non-disputable categories reject the dispute with MSG_CATEGORY_NOT_DISPUTABLE")
    void submitDispute_ShouldThrowBadRequest_ForNonDisputableCategory(TransactionCategory category) {
        // Transaction dated now() → inside the window, so only the category rule can block it.
        TransactionEntity transaction = saveTransaction(category, OffsetDateTime.now());
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        assertThat(category.isDisputable()).isFalse();
        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE);
    }

    @Test
    @DisplayName("Property 4: eligibility gate agrees with isDisputable() across every TransactionCategory")
    void submitDispute_ShouldMatchDisputableFlag_AcrossAllCategories() {
        for (TransactionCategory category : TransactionCategory.values()) {
            // Distinct transaction (unique reference) per category to avoid the duplicate guard.
            TransactionEntity transaction = saveTransaction(category, OffsetDateTime.now());
            DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

            if (category.isDisputable()) {
                DisputeResponseDto response = disputeService.submitDispute(customerId, request);
                assertThat(response).as("disputable category %s should succeed", category).isNotNull();
                assertThat(response.disputeReference()).startsWith("DSP-");
            } else {
                assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                        .as("non-disputable category %s should be rejected", category)
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE);
            }
        }
    }

    // =============================================================================================
    // Feature: regulatory-dispute-eligibility, Property 5: Blocked submissions create no dispute record.
    //
    // When a submission is blocked by a guard (non-disputable category, or an out-of-window date
    // on an otherwise disputable category), the dispute count MUST be unchanged — no DisputeEntity
    // is persisted. The guards run before the dispute is built/saved, and the rejected transaction
    // commits/rolls back as part of the single @Transactional submitDispute unit of work.
    // =============================================================================================
    // **Validates: Requirements 9.6**

    @ParameterizedTest(name = "blocked non-disputable category {0} persists no dispute record")
    @EnumSource(value = TransactionCategory.class, names = {
            "P2P_TRANSFER", "IN_STORE_RETAIL"
    })
    @DisplayName("Property 5: a non-disputable category rejection leaves the dispute count unchanged")
    void submitDispute_ShouldPersistNoDispute_WhenCategoryNotDisputable(TransactionCategory category) {
        TransactionEntity transaction = saveTransaction(category, OffsetDateTime.now());
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        long countBefore = disputeRepository.count();

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE);

        assertThat(disputeRepository.count())
                .as("no dispute record should be created for a non-disputable category")
                .isEqualTo(countBefore);
    }

    @Test
    @DisplayName("Property 5: an out-of-window dispute (61 days old) persists no dispute record")
    void submitDispute_ShouldPersistNoDispute_WhenOutsideDisputeWindow() {
        // Disputable category, but dated 61 days ago → outside the 60-day window under the real clock.
        TransactionEntity transaction =
                saveTransaction(TransactionCategory.ONLINE_RETAIL, OffsetDateTime.now().minusDays(61));
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        long countBefore = disputeRepository.count();

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_DISPUTE_WINDOW_CLOSED);

        assertThat(disputeRepository.count())
                .as("no dispute record should be created when the dispute window is closed")
                .isEqualTo(countBefore);
    }
}
