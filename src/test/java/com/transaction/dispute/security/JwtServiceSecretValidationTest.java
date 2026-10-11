package com.transaction.dispute.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the fail-fast JWT secret-key validation. A missing or too-weak signing key must
 * stop startup rather than fall back to an insecure default, so a committed key can never sign
 * production tokens.
 */
class JwtServiceSecretValidationTest {

    private JwtService newServiceWithKey(String key) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", key);
        return service;
    }

    @Test
    @DisplayName("validateSecretKey throws when the key is null")
    void validateSecretKey_ShouldThrow_WhenKeyNull() {
        assertThatThrownBy(() -> newServiceWithKey(null).validateSecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key");
    }

    @Test
    @DisplayName("validateSecretKey throws when the key is shorter than 32 bytes")
    void validateSecretKey_ShouldThrow_WhenKeyTooShort() {
        assertThatThrownBy(() -> newServiceWithKey("too-short-key").validateSecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    @DisplayName("validateSecretKey passes for a key of at least 32 bytes")
    void validateSecretKey_ShouldPass_WhenKeyStrongEnough() {
        String strongKey = "404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971";
        assertThatCode(() -> newServiceWithKey(strongKey).validateSecretKey())
                .doesNotThrowAnyException();
    }
}