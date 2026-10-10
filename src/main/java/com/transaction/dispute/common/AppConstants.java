package com.transaction.dispute.common;

public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    // Security & Auth Constants
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String DEFAULT_CURRENCY = "ZAR";
    public static final String DEFAULT_ACCOUNT_TYPE = "CHECKING";

    // Exception & Error Messages
    public static final String MSG_USER_NOT_FOUND_EMAIL = "User not found with email: ";
    public static final String MSG_EMAIL_ALREADY_EXISTS = "Email address is already registered";
    public static final String MSG_INVALID_CREDENTIALS = "Invalid email or password";
    public static final String MSG_TRANSACTION_NOT_FOUND = "Transaction not found with ID: ";
    public static final String MSG_ACCOUNT_NOT_FOUND = "Account not found for customer ID: ";
    public static final String MSG_DISPUTE_ALREADY_EXISTS = "A dispute has already been lodged for transaction ID: ";
    public static final String MSG_UNAUTHORIZED_TRANSACTION_ACCESS = "You are not authorized to dispute this transaction";
    public static final String MSG_INTERNAL_SERVER_ERROR = "An unexpected error occurred. Please try again later.";
    public static final String MSG_VALIDATION_FAILED = "Validation failed for request body";

    // Dispute Resolution Response Messages
    public static final String MSG_DISPUTE_AUTO_APPROVED = "Dispute evaluated by decision matrix: AUTO-APPROVED. Funds credited to your account.";
    public static final String MSG_DISPUTE_QUEUED_AGENT = "Dispute evaluated by decision matrix: Queued for back-office agent review.";
    public static final String MSG_DISPUTE_SUBMITTED = "Dispute submitted successfully.";
}