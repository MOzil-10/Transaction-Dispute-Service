package com.transaction.dispute.engine;

import com.transaction.dispute.model.enums.DecisionRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Evaluates the risk score and returns a routing decision.
 *
 * Rules:
 *  - score <= 50  → AUTO_APPROVE   (low risk, funds auto-credited)
 *  - score >  50  → MANUAL_INVESTIGATION (high risk, queued for agent)
 */
@Component
@Slf4j
public class DecisionMatrix {

    private static final int AUTO_APPROVE_THRESHOLD = 50;

    public DecisionRoute evaluate(int riskScore) {
        DecisionRoute route = riskScore <= AUTO_APPROVE_THRESHOLD
                ? DecisionRoute.AUTO_APPROVE
                : DecisionRoute.MANUAL_INVESTIGATION;
        log.debug("Decision evaluated: riskScore={}, route={}", riskScore, route);
        return route;
    }
}

