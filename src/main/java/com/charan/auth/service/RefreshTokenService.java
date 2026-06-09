package com.charan.auth.service;

import com.charan.auth.entity.RefreshToken;
import com.charan.auth.entity.User;
import com.charan.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${app.jwt.refresh-expiration}")
    private int refreshTokenExpiration; // 7 days in milliseconds

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Log for debugging
        System.out.println("Creating refresh token for user: " + user.getUsername());

        // Method 1: Delete by user directly
        try {
            refreshTokenRepository.deleteByUser(user);
            System.out.println("Deleted existing tokens by user");
        } catch (Exception e) {
            System.out.println("No existing token found or delete failed: " + e.getMessage());
        }

        // Method 2: Find and delete if exists
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUser(user);
        if (existingToken.isPresent()) {
            refreshTokenRepository.delete(existingToken.get());
            System.out.println("Deleted existing token by find");
        }

        // Create new refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        refreshToken.setRevoked(false);
        refreshToken.setCreatedAt(LocalDateTime.now());

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        System.out.println("Created new refresh token: " + saved.getToken());

        return saved;
    }

    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token has expired");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            System.out.println("Revoked refresh token: " + token);
        });
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
        System.out.println("Revoked all tokens for user: " + user.getUsername());
    }
}