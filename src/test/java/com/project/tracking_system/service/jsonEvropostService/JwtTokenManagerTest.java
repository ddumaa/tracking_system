package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.entity.EvropostServiceCredential;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.utils.EncryptionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link JwtTokenManager}.
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenManagerTest {

    @Mock
    private EncryptionUtils encryptionUtils;
    @Mock
    private GetJwtTokenService getJwtTokenService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtTokenManager tokenManager;

    @Test
    void scheduledTokenRefresh_UsesRepositoryMethod() {
        User user = new User();
        EvropostServiceCredential cred = new EvropostServiceCredential();
        cred.setUsername("login");
        cred.setPassword("encPass");
        cred.setServiceNumber("encSrv");
        cred.setUseCustomCredentials(true);
        cred.setTokenCreatedAt(ZonedDateTime.now(ZoneOffset.UTC).minusDays(30));
        user.setEvropostServiceCredential(cred);

        when(userRepository.findUsersForTokenRefresh(any())).thenReturn(List.of(user));
        try {
            when(encryptionUtils.decrypt("encPass")).thenReturn("pass");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            when(encryptionUtils.decrypt("encSrv")).thenReturn("srv");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(getJwtTokenService.getUserTokenFromApi("login", "pass", "srv")).thenReturn("new");

        tokenManager.scheduledTokenRefresh();

        ArgumentCaptor<ZonedDateTime> captor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(userRepository).findUsersForTokenRefresh(captor.capture());
        verify(userRepository).save(user);
        assertEquals("new", cred.getJwtToken());
        // Пороговая дата должна быть раньше текущего момента
        assertTrue(captor.getValue().isBefore(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    }
}
