package com.project.tracking_system.service.track;

import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Планировщик автоматического обновления треков.
 * <p>
 * Выполняет обход пользователей, для которых в тарифе
 * разрешено автоматическое обновление, и запускает обновление их треков.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackAutoUpdateScheduler {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserService userService;
    private final TrackAutoUpdateProcessor trackAutoUpdateProcessor;

    /**
     * Запускает автообновление треков для всех подходящих пользователей.
     *
     * <p>Метод не оборачивается в транзакцию, чтобы не держать
     * одну длительную сессию на всех пользователей. Каждое обновление
     * выполняется в собственной транзакции внутри
     * {@link TrackAutoUpdateProcessor#updateUserTracks(Long)}.</p>
     */
    public void updateAllUsersTracks() {
        List<Long> userIds = userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE);
        if (userIds.isEmpty()) {
            log.info("Нет пользователей с автообновлением треков");
            return;
        }

        for (Long userId : userIds) {
            if (userService.isAutoUpdateEnabled(userId)) {
                try {
                    trackAutoUpdateProcessor.updateUserTracks(userId);
                } catch (Exception e) {
                    log.error("Не удалось автообновить треки для userId={}", userId, e);
                }
            }
        }
    }
}
