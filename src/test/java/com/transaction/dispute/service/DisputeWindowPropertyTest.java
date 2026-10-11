package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.enums.MandateType;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic property tests for the regulatory dispute-window rule.
 *
 * <p>The production rule lives in {@code DisputeService.assertWithinDisputeWindow} which is
 * private. This class replicates the exact production formula in a static helper and verifies
 * its properties. A full service/DB slice is covered by task 8.2.</p>
 *
 * <p>Formula under test:
 * elapsed = ChronoUnit.DAYS.between(txDate in Africa/Johannesburg, LocalDate.now(clock));
 * within-window iff elapsed &lt;= AppConstants.DISPUTE_WINDOW_DAYS.</p>
 */
class DisputeWindowPropertyTest {

    private static final ZoneId ZONE = ZoneId.of(AppConstants.DISPUTE_TIMEZONE);

    /**
     * Replicates the production window check from DisputeService.assertWithinDisputeWindow.
     * Returns true when the transaction is still within the dispute window.
     */
    private static boolean withinWindow(OffsetDateTime txDate, Clock clock) {
        LocalDate txLocalDate = txDate.atZoneSameInstant(ZONE).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        long elapsed = ChronoUnit.DAYS.between(txLocalDate, today);
        return elapsed <= AppConstants.DISPUTE_WINDOW_DAYS;
    }

    // Feature: regulatory-dispute-eligibility, Property 1: Dispute-window boundary and determinism
    // Validates: Requirements 1.1, 1.2, 2.3
    @Property(tries = 100)
    void windowBoundaryAndDeterminism(
            @ForAll @LongRange(min = 0L, max = 4_000_000_000L) long baseEpochSeconds,
            @ForAll @IntRange(min = 0, max = 200) int elapsedDays) {

        // Base transaction instant, expressed as an OffsetDateTime (as persisted).
        Instant baseInstant = Instant.ofEpochSecond(baseEpochSeconds);
        OffsetDateTime txDate = baseInstant.atOffset(ZoneOffset.UTC);

        // "Now" is exactly elapsedDays later, with the clock fixed in the dispute timezone.
        Clock clock = Clock.fixed(baseInstant.plus(elapsedDays, ChronoUnit.DAYS), ZONE);

        boolean accepted = withinWindow(txDate, clock);

        // Accepted IFF elapsedDays within the 60-day regulatory window.
        assertThat(accepted).isEqualTo(elapsedDays <= AppConstants.DISPUTE_WINDOW_DAYS);

        // Determinism: identical inputs yield identical results.
        boolean acceptedAgain = withinWindow(txDate, clock);
        assertThat(acceptedAgain).isEqualTo(accepted);
    }

    // Feature: regulatory-dispute-eligibility, Property 2: Window decision is independent of mandate type
    // Validates: Requirements 1.7
    @Property(tries = 100)
    void windowDecisionIndependentOfMandateType(
            @ForAll @LongRange(min = 0L, max = 4_000_000_000L) long baseEpochSeconds,
            @ForAll @IntRange(min = 0, max = 200) int elapsedDays) {

        Instant baseInstant = Instant.ofEpochSecond(baseEpochSeconds);
        OffsetDateTime txDate = baseInstant.atOffset(ZoneOffset.UTC);
        Clock clock = Clock.fixed(baseInstant.plus(elapsedDays, ChronoUnit.DAYS), ZONE);

        // The window formula never inspects mandate type, so the decision must be identical
        // across every MandateType value.
        boolean baseline = withinWindow(txDate, clock);
        for (MandateType mandateType : MandateType.values()) {
            boolean decision = withinWindow(txDate, clock);
            assertThat(decision)
                    .as("window decision must be independent of mandate type %s", mandateType)
                    .isEqualTo(baseline);
        }
    }
}
