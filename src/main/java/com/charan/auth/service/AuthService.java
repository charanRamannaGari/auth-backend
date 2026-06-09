package com.charan.auth.service;

import com.charan.auth.dto.LoginRequest;
import com.charan.auth.dto.LoginResponse;
import com.charan.auth.dto.RefreshTokenRequest;
import com.charan.auth.dto.RegisterRequest;
import com.charan.auth.dto.TokenRefreshResponse;
import com.charan.auth.entity.RefreshToken;
import com.charan.auth.entity.Role;
import com.charan.auth.entity.User;
import com.charan.auth.repository.RefreshTokenRepository;
import com.charan.auth.repository.RoleRepository;
import com.charan.auth.repository.UserRepository;
import com.charan.auth.security.JwtUtil;
import com.charan.auth.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Authenticate user and generate access token + refresh token
     */
    @Transactional
    public LoginResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtil.generateToken(authentication);

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLastLogin(LocalDateTime.now());
        user.setFailedAttempts(0);
        userRepository.save(user);

        // First revoke any existing refresh tokens for this user
        refreshTokenService.revokeAllUserTokens(user);

        // Then create new refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return new LoginResponse(
                jwt,
                refreshToken.getToken(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roles
        );
    }

    /**
     * Register a new user
     */
    @Transactional
    public User registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName());
        user.setActive(true);
        user.setLocked(false);
        user.setFailedAttempts(0);

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.setRoles(Collections.singleton(userRole));

        return userRepository.save(user);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        // Verify refresh token
        RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(requestRefreshToken);
        User user = refreshToken.getUser();

        // Generate new access token
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String newAccessToken = jwtUtil.generateToken(authentication);

        // Rotate refresh token (create new one and revoke old one)
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);
        refreshTokenService.revokeRefreshToken(requestRefreshToken);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken.getToken());
    }

    /**
     * Logout user by revoking refresh token
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
    }

    /**
     * Logout from all devices by revoking all refresh tokens for user
     */
    @Transactional
    public void logoutAllDevices(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        refreshTokenService.revokeAllUserTokens(user);
    }

    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}