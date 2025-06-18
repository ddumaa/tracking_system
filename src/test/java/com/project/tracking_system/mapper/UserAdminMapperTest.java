package com.project.tracking_system.mapper;

import com.project.tracking_system.dto.UserListAdminInfoDTO;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link UserAdminMapper}.
 */
class UserAdminMapperTest {

    private final UserAdminMapper mapper = Mappers.getMapper(UserAdminMapper.class);

    @Test
    void mapsUserWithSubscription() {
        User user = new User();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setRole(Role.ROLE_ADMIN);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("PREMIUM");
        UserSubscription subscription = new UserSubscription();
        subscription.setSubscriptionPlan(plan);
        user.setSubscription(subscription);

        UserListAdminInfoDTO dto = mapper.toAdminListDto(user);

        assertEquals(1L, dto.getId());
        assertEquals("admin@example.com", dto.getEmail());
        assertEquals(Role.ROLE_ADMIN, dto.getRole());
        assertEquals("PREMIUM", dto.getSubscriptionPlanName());
    }

    @Test
    void mapsUserWithoutSubscription() {
        User user = new User();
        user.setId(2L);
        user.setEmail("user@example.com");
        user.setRole(Role.ROLE_USER);

        UserListAdminInfoDTO dto = mapper.toAdminListDto(user);

        assertEquals("NONE", dto.getSubscriptionPlanName());
    }
}
