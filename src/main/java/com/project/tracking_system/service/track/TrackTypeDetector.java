package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;

/**
 * Определяет тип почтовой службы для трека.
 * <p>
 * Интерфейс выделен для соблюдения принципа инверсии зависимостей:
 * {@link com.project.tracking_system.service.registration.PreRegistrationService}
 * взаимодействует с абстракцией, что упрощает тестирование и расширение.
 * </p>
 */
public interface TrackTypeDetector {

    /**
     * Выполняет определение сервиса доставки по метаданным трека.
     *
     * @param meta данные предрегистрации
     * @return тип почтовой службы; {@code UNKNOWN} при неудаче
     */
    PostalServiceType detect(PreRegistrationMeta meta);
}

