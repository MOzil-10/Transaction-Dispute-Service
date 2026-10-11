package com.transaction.dispute.model.enums;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link TransactionCategory} disputable classification.
 *
 * <p><b>Validates: Requirements 3.3, 3.4</b>
 */
class TransactionCategoryPropertyTest {

    private static final Set<TransactionCategory> DISPUTABLE = EnumSet.of(
            TransactionCategory.ONLINE_RETAIL,
            TransactionCategory.MOBILE_CONTRACT,
            TransactionCategory.GYM_MEMBERSHIP,
            TransactionCategory.LOAN,
            TransactionCategory.MUNICIPAL);

    private static final Set<TransactionCategory> NON_DISPUTABLE = EnumSet.of(
            TransactionCategory.P2P_TRANSFER,
            TransactionCategory.IN_STORE_RETAIL);

    @Provide
    Arbitrary<TransactionCategory> categories() {
        return Arbitraries.of(TransactionCategory.values());
    }

    // Feature: regulatory-dispute-eligibility, Property 3: Category disputable classification
    /**
     * For all {@link TransactionCategory} values, {@code isDisputable()} returns true exactly for
     * ONLINE_RETAIL, MOBILE_CONTRACT, GYM_MEMBERSHIP, LOAN, and MUNICIPAL, and false exactly for
     * P2P_TRANSFER and IN_STORE_RETAIL.
     *
     * <p><b>Validates: Requirements 3.3, 3.4</b>
     */
    @Property(tries = 200)
    void disputableClassificationMatchesSpecification(@ForAll("categories") TransactionCategory category) {
        boolean expectedDisputable = DISPUTABLE.contains(category);

        // Every category must fall into exactly one of the two sets.
        assertThat(DISPUTABLE.contains(category) ^ NON_DISPUTABLE.contains(category))
                .as("category %s must be exactly one of disputable or non-disputable", category)
                .isTrue();

        assertThat(category.isDisputable())
                .as("isDisputable() for %s", category)
                .isEqualTo(expectedDisputable);
    }
}
