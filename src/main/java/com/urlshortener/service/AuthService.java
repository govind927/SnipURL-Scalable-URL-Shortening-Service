package com.urlshortener.service;

import com.urlshortener.dto.AuthDto;
import com.urlshortener.model.User;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration and login.
 *
 * Register flow:
 *  1. Check email not already taken
 *  2. Hash password with BCrypt
 *  3. Save user to DB
 *  4. Return JWT token (auto-login after register)
 *
 * Login flow:
 *  1. AuthenticationManager verifies email + password
 *  2. Load user from DB
 *  3. Generate and return JWT token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtService            jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", request.getEmail());

        // Auto-login — return token immediately after register
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .message("Registration successful")
                .build();
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        // This throws if credentials are wrong — Spring Security handles it
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User logged in: {}", request.getEmail());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .message("Login successful")
                .build();
    }
}
