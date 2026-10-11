package com.transaction.dispute;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit smoke tests (no Spring context) that lock down the regulatory constants,
 * enum shapes, and distinct user-facing messages used by the dispute decision rules.
 *
 * NOTE: Schema/migration validation (Req 3.6/3.7/8.4) is NOT re-tested here. It is already
 * proven by the existing @SpringBootTest suite (e.g. DisputeApplicationTests), which boots
 * under ddl-auto: validate on H2 — the application context would fail to start if the JPA
 * entity model and the H2 migrations disagreed. No redundant context-load test is added.
 */
class RegulatoryConstantsSmokeTest {

    // Validates: Requirements 9.1 (60-day dispute window)
    @Test
    @DisplayName("Dispute window is 60 days")
    void disputeWindowDays_isSixty() {
        assertThat(AppConstants.DISPUTE_WINDOW_DAYS).isEqualTo(60);
    }

    // Validates: Requirements 9.1 (SAST / Africa/Johannesburg timezone)
    @Test
    @DisplayName("Dispute timezone is Africa/Johannesburg")
    void disputeTimezone_isJohannesburg() {
        assertThat(AppConstants.DISPUTE_TIMEZONE).isEqualTo("Africa/Johannesburg");
    }

    // Validates: Requirements 9.3 (distinct messages for window-closed vs category-not-disputable)
    @Test
    @DisplayName("Window-closed and category-not-disputable messages are non-blank and distinct")
    void disputeRejectionMessages_areNonBlankAndDistinct() {
        assertThat(AppConstants.MSG_DISPUTE_WINDOW_CLOSED).isNotBlank();
        assertThat(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE).isNotBlank();
        assertThat(AppConstants.MSG_DISPUTE_WINDOW_CLOSED)
                .isNotEqualTo(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE);
    }

    // Validates: Requirements (transaction category taxonomy)
    @Test
    @DisplayName("TransactionCategory has exactly the 7 expected values")
    void transactionCategory_hasSevenExpectedValues() {
        assertThat(TransactionCategory.values()).hasSize(7);
        assertThat(Arrays.stream(TransactionCategory.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "ONLINE_RETAIL",
                        "MOBILE_CONTRACT",
                        "GYM_MEMBERSHIP",
                        "LOAN",
                        "MUNICIPAL",
                        "P2P_TRANSFER",
                        "IN_STORE_RETAIL");
    }

    // Validates: Requirements 3.8 (REGISTERED_MANDATE mandate type exists)
    @Test
    @DisplayName("MandateType includes REGISTERED_MANDATE")
    void mandateType_includesRegisteredMandate() {
        assertThat(Arrays.stream(MandateType.values()).map(Enum::name))
                .contains("REGISTERED_MANDATE");
    }
}
