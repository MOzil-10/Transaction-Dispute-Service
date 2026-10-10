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
import com.transaction.dispute.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        log.info("Processing registration request");

        if (userRepository.existsByEmail(request.email())) {
            log.error("Registration rejected â€” email already in use: {}", request.email());
            throw new BadRequestException(AppConstants.MSG_EMAIL_ALREADY_EXISTS);
        }

        UserEntity user = UserEntity.builder()
                .customerNumber("CUST-" + System.currentTimeMillis())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        UserEntity savedUser = userRepository.save(user);
        log.debug("Persisted new customer record: customerId={}", savedUser.getCustomerId());

        AccountEntity account = AccountEntity.builder()
                .user(savedUser)
                .accountNumber(String.valueOf((long) (Math.random() * 9000000000L) + 1000000000L))
                .accountType(AppConstants.DEFAULT_ACCOUNT_TYPE)
                .balance(new BigDecimal("10000.00"))
                .currency(AppConstants.DEFAULT_CURRENCY)
                .build();

        accountRepository.save(account);
        log.debug("Provisioned default {} checking account for customerId={}", AppConstants.DEFAULT_CURRENCY, savedUser.getCustomerId());

        var userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtService.generateToken(userDetails, savedUser.getCustomerId());

        log.info("Registration complete â€” customerId={} provisioned with checking account", savedUser.getCustomerId());
        return new AuthResponseDto(token, savedUser.getCustomerId(), savedUser.getEmail());
    }

    public AuthResponseDto login(LoginRequestDto request) {
        log.info("Processing login request");

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            log.error("Login failed â€” invalid credentials for email: {}", request.email());
            throw new UnauthorizedException(AppConstants.MSG_INVALID_CREDENTIALS);
        }

        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.error("Login failed â€” no user found for email: {}", request.email());
                    return new UnauthorizedException(AppConstants.MSG_INVALID_CREDENTIALS);
                });

        var userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails, user.getCustomerId());

        log.info("Login successful â€” customerId={} authenticated", user.getCustomerId());
        return new AuthResponseDto(token, user.getCustomerId(), user.getEmail());
    }
}
