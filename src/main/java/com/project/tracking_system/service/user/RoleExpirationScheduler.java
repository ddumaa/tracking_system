package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.TrackParcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
@Slf4j
@RequiredArgsConstructor
@Service
public class RoleExpirationScheduler {

    private final UserRepository userRepository;
    private final TrackParcelService trackParcelService;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void checkExpiredRoles() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        // Получаем только тех пользователей, у кого срок действия истек
        List<User> expiredUsers = userRepository.findExpiredPaidUsers(nowUtc);
        List<User> usersToUpdate = new ArrayList<>();

        for (User user : expiredUsers) {
            user.getRoles().remove(Role.ROLE_PAID_USER);
            user.getRoles().add(Role.ROLE_FREE_USER);
            user.setRoleExpirationDate(null);
            usersToUpdate.add(user);
            log.info("Пользователь с ID {} переведен на бесплатный тариф.", user.getId());
        }

        // Обновляем только измененных пользователей
        if (!usersToUpdate.isEmpty()) {
            userRepository.saveAll(usersToUpdate);
            log.info("Обновлены {} пользователей с истекшим сроком подписки.", usersToUpdate.size());
        }

        // Обновляем историю только для тех, у кого подписка еще активна
        List<Long> activePaidUsers = userRepository.findActivePaidUsers(nowUtc);
        for (Long userId : activePaidUsers) {
            trackParcelService.updateHistory(userId);
        }
        log.info("Обновлена история отслеживания для {} платных пользователей.", activePaidUsers.size());
    }

}