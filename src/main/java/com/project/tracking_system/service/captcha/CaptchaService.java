package com.project.tracking_system.service.captcha;

/**
 * Сервис проверки капча-токенов.
 * <p>
 * Предоставляет абстракцию для проверки того, что запрос отправлен человеком,
 * что соответствует принципу разделения ответственности (SOLID).
 * </p>
 */
public interface CaptchaService {

    /**
     * Проверяет валидность токена, выданного виджетом reCAPTCHA.
     *
     * @param token   токен, полученный от клиента
     * @param ip      IP-адрес пользователя для дополнительной проверки
     * @return {@code true}, если токен прошел проверку
     */
    boolean verifyToken(String token, String ip);

    /**
     * Возвращает публичный ключ сайта, необходимый для рендеринга виджета.
     *
     * @return публичный ключ
     */
    String getSiteKey();
}
