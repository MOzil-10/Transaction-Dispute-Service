package com.transaction.dispute.engine;

import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.MandateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates a risk score (0-100) for a dispute submission.
 *
 * Scoring factors:
 *  - DEBICHECK mandate:        +30 (pre-authorised, lower risk of fraud)
 *  - STANDARD_EFT mandate:     +50 (higher dispute likelihood)
 *  - UNAUTHORISED_MANDATE:     +20 additional (high-risk reason)
 *  - CANCELLED_CONTRACT:       +15 additional
 *  - DUPLICATE_DEBIT:          +15 additional
 *  - INCORRECT_AMOUNT:         +10 additional
 *  - INCORRECT_DATE:           + 5 additional
 *
 * Score is capped at 100.
 *
 * Rationale: DEBICHECK is a pre-authorised mandate the customer approved up front, so it
 * carries lower fraud risk (base 30) than an unauthenticated STANDARD_EFT debit (base 50).
 * Combined with the decision threshold of 50, this is a deliberate, conservative policy:
 * DEBICHECK disputes with a low-severity reason can auto-approve, while every STANDARD_EFT
 * dispute (base 50 + any reason >= 5) exceeds the threshold and is routed to a human agent.
 * The weights are illustrative defaults for this assessment; in production they would be
 * tuned against historical dispute outcomes and signed off by the risk/fraud team.
 */
@Component
@Slf4j
public class RiskScoreEngine {

    public int calculate(TransactionEntity transaction, DisputeReason reason) {
        int score = 0;

        // Base score from mandate type
        if (MandateType.DEBICHECK.equals(transaction.getMandateType())) {
            score += 30;
        } else {
            score += 50;
        }

        // Incremental score from dispute reason
        score += switch (reason) {
            case UNAUTHORISED_MANDATE -> 20;
            case CANCELLED_CONTRACT   -> 15;
            case DUPLICATE_DEBIT      -> 15;
            case INCORRECT_AMOUNT     -> 10;
            case INCORRECT_DATE       ->  5;
        };

        int capped = Math.min(score, 100);
        log.debug("Risk score calculated: {} (mandate={}, reason={})", capped, transaction.getMandateType(), reason);
        return capped;
    }
}

