package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.TrackParcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Метод проверки роли пользователей
 * и срока её истечения, если срок истечения наступил
 * изменяет роль
 * <p>
 * каждую ночь в 3:00 UTC
 *
 * @author Dmitriy Anisimov
 * @date 07.02.2025
 */
@Service
public class RoleExpirationScheduler {

    private final UserRepository userRepository;
    private final TrackParcelService trackParcelService;

    @Autowired
    public RoleExpirationScheduler(UserRepository userRepository, TrackParcelService trackParcelService) {
        this.userRepository = userRepository;
        this.trackParcelService = trackParcelService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void checkExpiredRoles() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        // Получаем пользователей с ролью ROLE_PAID_USER
        List<User> paidUsers = userRepository.findUsersByRole(Role.ROLE_PAID_USER);

        for (User user : paidUsers) {
            ZonedDateTime expiry = user.getRoleExpirationDate();

            // Проверяем, если срок действия роли истек
            if (expiry != null && expiry.isBefore(nowUtc)) {
                // Обновляем роль с ROLE_PAID_USER на ROLE_FREE_USER
                if (user.getRoles().contains(Role.ROLE_PAID_USER)) {
                    user.getRoles().remove(Role.ROLE_PAID_USER);
                    user.getRoles().add(Role.ROLE_FREE_USER);
                    user.setRoleExpirationDate(null);
                }
            } else {
                // Если роль ещё активна, обновляем историю отслеживания
                trackParcelService.updateHistory(user);
            }
        }
        // Сохраняем все обновленные пользователи
        userRepository.saveAll(paidUsers);
    }
}
