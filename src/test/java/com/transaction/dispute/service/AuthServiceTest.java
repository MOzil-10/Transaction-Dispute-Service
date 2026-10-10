package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.UnauthorizedException;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.LoginRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.context.annotation.Import(com.transaction.dispute.config.TestSqsConfig.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // This test verifies that registering a new customer persists the user entity in H2 database
    // and automatically provisions a default checking account associated with that user.
    @Test
    @DisplayName("Should successfully register user and provision checking account in H2 database")
    void register_ShouldSaveUserAndProvisionAccount() {
        // Given
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();

        // When
        AuthResponseDto response = authService.register(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo(request.email());

        // Verify User was saved in H2 DB
        Optional<UserEntity> savedUser = userRepository.findByEmail(request.email());
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getFirstName()).isEqualTo(request.firstName());

        // Verify Default Checking Account was provisioned in H2 DB
        Optional<AccountEntity> savedAccount = accountRepository.findByUserCustomerId(savedUser.get().getCustomerId());
        assertThat(savedAccount).isPresent();
        assertThat(savedAccount.get().getAccountType()).isEqualTo(AppConstants.DEFAULT_ACCOUNT_TYPE);
        assertThat(savedAccount.get().getCurrency()).isEqualTo(AppConstants.DEFAULT_CURRENCY);
    }

    // This test verifies that trying to register with an email address that already exists
    // in the H2 database triggers a BadRequestException with the correct constant error message.
    @Test
    @DisplayName("Should throw BadRequestException when registering with duplicate email")
    void register_ShouldThrowException_WhenEmailAlreadyExists() {
        // Given
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        authService.register(request);

        // When / Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_EMAIL_ALREADY_EXISTS);
    }

    // This test verifies that a registered customer can log in with valid credentials
    // and receive a signed JWT token containing their user information.
    @Test
    @DisplayName("Should successfully authenticate user and return JWT token on login")
    void login_ShouldAuthenticateAndReturnJwt_WhenCredentialsAreValid() {
        // Given
        RegisterRequestDto registerRequest = TestPayloadFactory.createRegisterRequest();
        authService.register(registerRequest);

        LoginRequestDto loginRequest = TestPayloadFactory.createLoginRequest();

        // When
        AuthResponseDto response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo(loginRequest.email());
    }

    // This test verifies that attempting to log in with an incorrect password
    // throws an UnauthorizedException and denies access.
    @Test
    @DisplayName("Should throw UnauthorizedException when login password is invalid")
    void login_ShouldThrowException_WhenPasswordIsIncorrect() {
        // Given
        RegisterRequestDto registerRequest = TestPayloadFactory.createRegisterRequest();
        authService.register(registerRequest);

        LoginRequestDto loginRequest = new LoginRequestDto("rita.sithole@example.co.za", "WrongPassword!");

        // When / Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AppConstants.MSG_INVALID_CREDENTIALS);
    }
}