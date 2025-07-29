package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.PasswordResetToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.PasswordResetTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.utils.HashUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link PasswordResetService}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private RandomlyGeneratedString randomStringGenerator;

    @InjectMocks
    private PasswordResetService service;

    @Test
    void createPasswordResetToken_SavesHashedToken() {
        String email = "user@example.com";
        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(randomStringGenerator.generateConfirmationCode()).thenReturn("rawtok");
        when(tokenRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.createPasswordResetToken(email);

        verify(tokenRepository).save(argThat(t ->
                HashUtils.sha256("rawtok").equals(t.getToken()) && email.equals(t.getEmail())
        ));
        verify(emailService).sendPasswordResetEmail(eq(email), contains("rawtok"));
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        String token = "tok123";
        String hashed = HashUtils.sha256(token);
        PasswordResetToken resetToken = new PasswordResetToken("a@b.com", hashed);
        resetToken.setExpirationDate(ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        when(tokenRepository.findByToken(hashed)).thenReturn(Optional.of(resetToken));

        assertTrue(service.isTokenValid(token));
    }
}

