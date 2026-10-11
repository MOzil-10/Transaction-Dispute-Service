package com.transaction.dispute.mapper;

import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.model.enums.TransactionStatus;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: regulatory-dispute-eligibility, Property 6: Response disputable flag mirrors category
class TransactionMapperPropertyTest {

    private final TransactionMapper mapper = Mappers.getMapper(TransactionMapper.class);

    /**
     * Property 6: The disputable flag on the mapped TransactionResponseDto must always
     * equal the disputable attribute of the source entity's TransactionCategory.
     *
     * Validates: Requirements 5.1, 5.2, 5.3
     */
    @Property(tries = 100)
    void responseDisputableFlagMirrorsCategory(@ForAll("categories") TransactionCategory category) {
        TransactionEntity entity = TransactionEntity.builder()
                .transactionId(5000102L)
                .reference("TXN-90812")
                .merchant("FITNESS_CLUB_ZA")
                .amount(new BigDecimal("450.00"))
                .currency("ZAR")
                .mandateType(MandateType.STANDARD_EFT)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(OffsetDateTime.now())
                .category(category)
                .build();

        assertThat(mapper.toDto(entity).disputable())
                .isEqualTo(entity.getCategory().isDisputable());
    }

    @Provide
    Arbitrary<TransactionCategory> categories() {
        return Arbitraries.of(TransactionCategory.values());
    }
}
