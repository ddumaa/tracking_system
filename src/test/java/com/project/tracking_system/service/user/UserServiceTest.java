package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.dto.UserProfileDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.EvropostServiceCredentialRepository;
import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.tariff.TariffService;
import com.project.tracking_system.utils.EncryptionUtils;
import com.project.tracking_system.utils.RandomlyGeneratedString;
import com.project.tracking_system.utils.UserCredentialsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link UserService}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EvropostServiceCredentialRepository evropostServiceCredentialRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private RandomlyGeneratedString randomlyGeneratedString;
    @Mock
    private ConfirmationTokenRepository confirmationTokenRepository;
    @Mock
    private EncryptionUtils encryptionUtils;
    @Mock
    private JwtTokenManager jwtTokenManager;
    @Mock
    private UserCredentialsResolver userCredentialsResolver;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;
    @Mock
    private TariffService tariffService;

    @InjectMocks
    private UserService userService;

    @Test
    void getUserProfile_FillsPlanDetails() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setTimeZone("Europe/Minsk");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PREMIUM");
        UserSubscription subscription = new UserSubscription();
        subscription.setSubscriptionPlan(plan);
        subscription.setAutoUpdateEnabled(false);
        subscription.setSubscriptionEndDate(ZonedDateTime.now(ZoneOffset.UTC));
        user.setSubscription(subscription);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        SubscriptionPlanViewDTO planDto = new SubscriptionPlanViewDTO();
        planDto.setCode("PREMIUM");
        when(tariffService.getPlanInfoByCode("PREMIUM")).thenReturn(planDto);

        UserProfileDTO dto = userService.getUserProfile(1L);

        assertEquals("user@example.com", dto.getEmail());
        assertFalse(dto.isAutoUpdateEnabled());
        assertNotNull(dto.getPlanDetails());
        assertEquals("PREMIUM", dto.getPlanDetails().getCode());
        verify(tariffService).getPlanInfoByCode("PREMIUM");
    }
}
