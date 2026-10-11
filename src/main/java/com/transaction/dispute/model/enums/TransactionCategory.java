package com.transaction.dispute.model.enums;

public enum TransactionCategory {
    ONLINE_RETAIL(true),
    MOBILE_CONTRACT(true),
    GYM_MEMBERSHIP(true),
    LOAN(true),
    MUNICIPAL(true),
    P2P_TRANSFER(false),
    IN_STORE_RETAIL(false);

    private final boolean disputable;

    TransactionCategory(boolean disputable) {
        this.disputable = disputable;
    }

    public boolean isDisputable() {
        return disputable;
    }
}
