package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.dto.UserProfileDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.utils.EncryptionUtils;
import com.project.tracking_system.utils.EmailUtils;
import com.project.tracking_system.utils.UserCredentialsResolver;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;

/**
 * Сервис для управления пользователями.
 * <p>
 * Этот сервис обрабатывает операции, связанные с пользователями,
 * включая регистрацию, подтверждение регистрации, смену пароля и удаление пользователя.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final EvropostServiceCredentialRepository evropostServiceCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RandomlyGeneratedString randomlyGeneratedString;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final EncryptionUtils encryptionUtils;
    private final JwtTokenManager jwtTokenManager;
    private final UserCredentialsResolver userCredentialsResolver;
    private final SubscriptionService subscriptionService;
    private final StoreService storeService;

    /**
     * Отправляет код подтверждения на email для регистрации нового пользователя.
     * <p>
     * Метод генерирует код подтверждения, создает токен подтверждения и отправляет ссылку для подтверждения регистрации.
     * Если токен уже существует, его данные обновляются.
     * </p>
     *
     * @param userDTO DTO с данными нового пользователя.
     * @throws UserAlreadyExistsException Если пользователь с указанным email уже существует.
     */
    @Transactional
    public void sendConfirmationCode(UserRegistrationDTO userDTO) {
        String email = userDTO.getEmail();
        log.info("Начало отправки кода подтверждения для {}", EmailUtils.maskEmail(email));

        if (isEmailAlreadyRegistered(email)) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует.");
        }

        String confirmationCode = randomlyGeneratedString.generateConfirmationCode();
        saveOrUpdateConfirmationToken(email, confirmationCode);

        // Отправка email в фоне (не блокируем основной поток)
        emailService.sendConfirmationEmail(email, confirmationCode);

        log.info("Отправка кода подтверждения для {} успешно завершена", EmailUtils.maskEmail(email));
    }

    /**
     * Проверяет, зарегистрирован ли email в системе.
     */
    private boolean isEmailAlreadyRegistered(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    /**
     * Обновляет код подтверждения, если токен существует, или создает новый.
     */
    private void saveOrUpdateConfirmationToken(String email, String confirmationCode) {
        confirmationTokenRepository.findByEmail(email)
                .ifPresentOrElse(
                        token -> {
                            token.setConfirmationCode(confirmationCode);
                            token.setCreatedAt(ZonedDateTime.now(UTC));
                            confirmationTokenRepository.save(token);
                            log.info("Обновлен код подтверждения для email: {}", EmailUtils.maskEmail(email));
                        },
                        () -> {
                            ConfirmationToken newToken = new ConfirmationToken(email, confirmationCode);
                            confirmationTokenRepository.save(newToken);
                            log.info("Создан новый код подтверждения для email: {}", EmailUtils.maskEmail(email));
                        }
                );
    }

    /**
     * Подтверждает регистрацию нового пользователя с использованием кода подтверждения.
     * <p>
     * Метод проверяет код подтверждения и время его создания. Если код действителен, создается новый пользователь и его данные сохраняются.
     * </p>
     *
     * @param userDTO DTO с данными пользователя, включая код подтверждения.
     * @throws IllegalArgumentException Если код подтверждения неверный или срок его действия истек.
     */
    @Transactional
    public void confirmRegistration(UserRegistrationDTO userDTO) {
        log.info("Начало подтверждения регистрации для {}", EmailUtils.maskEmail(userDTO.getEmail()));

        ConfirmationToken token = confirmationTokenRepository.findByEmail(userDTO.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Код подтверждения не найден"));

        ZonedDateTime tokenCreatedAt = token.getCreatedAt();
        ZonedDateTime oneHourAgoUtc = ZonedDateTime.now(UTC).minusHours(1);

        if (tokenCreatedAt.isBefore(oneHourAgoUtc)) {
            log.warn("Код подтверждения для email {} истек", EmailUtils.maskEmail(userDTO.getEmail()));
            throw new IllegalArgumentException("Срок действия кода подтверждения истек");
        }

        if (!token.getConfirmationCode().equals(userDTO.getConfirmCodRegistration())) {
            log.warn("Неверный код подтверждения для email {}", EmailUtils.maskEmail(userDTO.getEmail()));
            throw new IllegalArgumentException("Неверный код подтверждения");
        }

        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setTimeZone("Europe/Minsk");
        user.setRole(Role.ROLE_USER);

        UserSubscription subscription = subscriptionService.createDefaultSubscriptionForUser(user);
        user.setSubscription(subscription);

        userRepository.save(user);
        storeService.createDefaultStoreForUser(user);
        confirmationTokenRepository.deleteByEmail(userDTO.getEmail());

        log.info("Пользователь {} успешно создан, код подтверждения удалён", EmailUtils.maskEmail(userDTO.getEmail()));
    }

    /**
     * Обновляет роль пользователя.
     *
     * @param userId  идентификатор пользователя
     * @param newRole новая роль
     */
    public void updateUserRole(Long userId, String newRole) {
        log.info("Начало смены роли пользователя ID={} на {}", userId, newRole);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        try {
            Role role = Role.valueOf(newRole); // Проверяем, что роль существует

            // Если у пользователя уже есть такая роль, ничего не делаем
            if (user.getRole() == role) {
                log.info("Роль пользователя с ID {} уже установлена на {}", userId, newRole);
                return;
            }

            user.setRole(role);
            userRepository.save(user);

            log.info("Роль пользователя с ID {} успешно обновлена до {}", userId, newRole);
        } catch (IllegalArgumentException e) {
            log.error("Ошибка обновления роли: Некорректное значение '{}'", newRole, e);
            throw new IllegalArgumentException("Некорректная роль: " + newRole);
        }
    }

    /**
     * Создаёт пользователя по заданным данным без подтверждения email.
     * <p>
     * После создания пользователя формируется бесплатная подписка и магазин по умолчанию,
     * затем подписка переключается на указанный тарифный план.
     * </p>
     *
     * @param email       адрес электронной почты
     * @param rawPassword пароль в открытом виде
     * @param roleName    наименование роли
     * @param planCode    стартовый тариф
     * @throws UserAlreadyExistsException если пользователь уже существует
     */
    @Transactional
    public void createUserByAdmin(String email, String rawPassword, String roleName,
                                 String planCode) {
        log.info("Администратор создаёт пользователя {}", EmailUtils.maskEmail(email));

        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует.");
        }

        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            log.error("Некорректная роль '{}' при создании пользователя", roleName, e);
            throw new IllegalArgumentException("Некорректная роль: " + roleName);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setTimeZone("Europe/Minsk");
        user.setRole(role);

        // Создаём подписку FREE по умолчанию
        UserSubscription subscription = subscriptionService.createDefaultSubscriptionForUser(user);
        user.setSubscription(subscription);

        // Сохраняем пользователя вместе с созданной подпиской
        userRepository.save(user);

        // Создаём магазин по умолчанию
        storeService.createDefaultStoreForUser(user);

        // Переключаем подписку на указанный тариф
        subscriptionService.changeSubscription(user.getId(), planCode, null);
        log.info("Пользователь {} создан администратором", EmailUtils.maskEmail(email));
    }

    /**
     * Обновляет настройки и учётные данные Evropost пользователя.
     *
     * @param userId идентификатор пользователя
     * @param dto    новые учётные данные
     */
    public void updateEvropostCredentialsAndSettings(Long userId, EvropostCredentialsDTO dto) {
        log.info("Начало обновления данных Evropost для пользователя ID={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        try {
            // Шифрование пароля и номера сервиса
            String encryptedPassword = encryptionUtils.encrypt(dto.getEvropostPassword());
            String encryptedServiceNumber = encryptionUtils.encrypt(dto.getServiceNumber());



            // Обновление всех данных
            user.getEvropostServiceCredential().setUsername(dto.getEvropostUsername());
            user.getEvropostServiceCredential().setPassword(encryptedPassword);
            user.getEvropostServiceCredential().setServiceNumber(encryptedServiceNumber);
            user.getEvropostServiceCredential().setUseCustomCredentials(dto.getUseCustomCredentials());

            userRepository.save(user);
            log.info("Данные успешно обновлены для пользователя с ID: {}", userId);

        } catch (Exception e) {
            log.error("Ошибка при шифровании данных для пользователя с ID: {}", userId, e);
            throw new RuntimeException("Ошибка при шифровании данных", e);
        }

        // Генерация нового JWT токена
        String newToken = jwtTokenManager.getUserToken(user);
        if (newToken == null) {
            log.error("Не удалось создать JWT токен для пользователя с ID: {}", userId);
            throw new RuntimeException("Не удалось создать JWT токен");
        }

        log.info("Новый JWT токен успешно создан для пользователя с ID: {}", userId);
    }

    /**
     * Обновляет флаг использования собственных учётных данных Evropost.
     *
     * @param userId              идентификатор пользователя
     * @param useCustomCredentials новое значение флага
     */
    public void updateUseCustomCredentials(Long userId, Boolean useCustomCredentials) {
        log.info("Начало обновления флага useCustomCredentials для пользователя ID={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        EvropostServiceCredential credentials = user.getEvropostServiceCredential();

        if (credentials == null) {
            log.warn("Попытка обновить Evropost настройки, но они отсутствуют у пользователя ID={}", userId);
            // Вариант 1: создать новый объект, если это допустимо
            credentials = new EvropostServiceCredential();
            credentials.setUser(user);
            user.setEvropostServiceCredential(credentials);
        }

        credentials.setUseCustomCredentials(useCustomCredentials);
        userRepository.save(user);

        log.info("Флаг 'useCustomCredentials' обновлён для пользователя с ID {}: {}", userId, useCustomCredentials);
    }

    /**
     * Проверяет, включено ли автообновление треков у пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если автообновление разрешено
     */
    public boolean isAutoUpdateEnabled(Long userId) {
        return userSubscriptionRepository.isAutoUpdateEnabled(userId);
    }

    /**
     * Обновляет настройку автообновления треков пользователя.
     *
     * @param userId  идентификатор пользователя
     * @param enabled новое значение флага
     */
    public void updateAutoUpdateEnabled(Long userId, boolean enabled) {
        userSubscriptionRepository.updateAutoUpdateEnabled(userId, enabled);
        log.info("Настройка autoUpdateEnabled обновлена для пользователя {}: {}", userId, enabled);
    }

    /**
     * Получает сохранённые учётные данные Evropost пользователя.
     *
     * @param userId идентификатор пользователя
     * @return DTO с именем пользователя и флагом использования своих данных
     */
    public EvropostCredentialsDTO getEvropostCredentials(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        EvropostServiceCredential credentials = user.getEvropostServiceCredential();

        if (credentials == null) {
            log.warn("У пользователя с ID {} отсутствуют учётные данные Evropost", userId);
            return new EvropostCredentialsDTO(); // или выбросить исключение, если это ошибка логики
        }

        EvropostCredentialsDTO dto = new EvropostCredentialsDTO();
        dto.setEvropostUsername(credentials.getUsername());
        dto.setUseCustomCredentials(credentials.getUseCustomCredentials());

        log.info("Запрошены учетные данные Evropost для пользователя с ID {}", userId);

        return dto;
    }

    /**
     * Находит пользователя по его email.
     *
     * @param email Email пользователя.
     * @return Опциональный объект с пользователем.
     */
    public Optional<User> findByUserEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Возвращает пользователя по его идентификатору.
     *
     * @param userId идентификатор пользователя
     * @return найденный пользователь
     * @throws IllegalArgumentException если пользователь не найден
     */
    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    /**
     * Получает список всех пользователей системы.
     *
     * @return список пользователей
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Возвращает общее количество пользователей в системе.
     *
     * @return число пользователей
     */
    public long countUsers() {
        return userRepository.count();
    }

    /**
     * Разрешает учётные данные пользователя для работы с сервисом Evropost.
     *
     * @param userId идентификатор пользователя
     * @return объект с расшифрованными учётными данными
     * @throws UsernameNotFoundException если пользователь не найден
     */
    public ResolvedCredentialsDTO resolveCredentials(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));
        return userCredentialsResolver.resolveCredentials(user);
    }

    /**
     * Проверяет, использует ли пользователь собственные учётные данные Evropost.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если используется свой набор учётных данных
     */
    public boolean isUsingCustomCredentials(Long userId) {
        return evropostServiceCredentialRepository.isUsingCustomCredentials(userId);
    }

    /**
     * Подсчитывает пользователей по коду тарифного плана.
     *
     * @param code код тарифного плана (например, FREE, PREMIUM)
     * @return количество пользователей с указанным тарифом
     */
    public long countUsersBySubscriptionPlan(String code) {
        return userRepository.countBySubscription_SubscriptionPlan_Code(code);
    }

    /**
     * Подсчитывает количество пользователей с платными тарифами.
     *
     * @return число пользователей, использующих платные планы
     */
    public long countPaidUsers() {
        return userRepository.countBySubscription_SubscriptionPlan_PriceGreaterThan(BigDecimal.ZERO);
    }

    /**
     * Меняет пароль пользователя.
     * <p>
     * Метод проверяет правильность текущего пароля, и если он верен, обновляет пароль на новый.
     * </p>
     *
     * @param email            Email пользователя.
     * @param userSettingsDTO DTO с новыми данными пользователя.
     * @throws IllegalArgumentException Если текущий пароль неверен или пользователь не найден.
     */
    public void changePassword(Long userId, UserSettingsDTO userSettingsDTO) {
        log.info("Начало смены пароля пользователя ID={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        if (!passwordEncoder.matches(userSettingsDTO.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Текущий пароль введён неверно");
        }

        user.setPassword(passwordEncoder.encode(userSettingsDTO.getNewPassword()));
        userRepository.save(user);

        log.info("Пароль пользователя с ID {} был успешно изменен.", userId);
    }

    /**
     * Удаляет текущего пользователя.
     * <p>
     * Метод удаляет пользователя из базы данных на основе данных текущей аутентификации.
     * </p>
     */
    public void deleteUser(Long userId) {
        log.info("Начало удаления пользователя ID={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        userRepository.delete(user);
        log.info("Пользователь с ID {} был удален.", userId);
    }

    /**
     * Получает лимит магазинов пользователя.
     *
     * @param userId ID пользователя.
     * @return Количество использованных и доступных магазинов в виде строки "1/10".
     */
    @Transactional
    public String getUserStoreLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        int storeCount = user.getStores().size(); // Получаем количество магазинов
        int maxStores = Optional.ofNullable(user.getSubscription())
                .map(UserSubscription::getSubscriptionPlan)
                .map(SubscriptionPlan::getLimits)
                .map(SubscriptionLimits::getMaxStores)
                .orElse(1); // По умолчанию 1

        return storeCount + "/" + maxStores;
    }

    /**
     * Определяет ID текущего пользователя из объекта аутентификации.
     *
     * @param authentication текущая аутентификация Spring Security
     * @return идентификатор пользователя или {@code null}, если пользователь не определён
     */
    public Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    /**
     * Получает часовой пояс пользователя.
     *
     * @param userId идентификатор пользователя
     * @return объект {@link ZoneId} с часовым поясом пользователя
     * @throws IllegalArgumentException если пользователь не найден
     */
    public ZoneId getUserZone(Long userId) {
        return ZoneId.of(userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден")).getTimeZone());
    }

    /**
     * Возвращает профиль пользователя.
     * <p>
     * Метод извлекает пользователя и связанную подписку, формируя DTO с основной информацией.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @return данные профиля пользователя
     */
    @Transactional
    public UserProfileDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        UserSubscription subscription = user.getSubscription();

        String planCode = Optional.ofNullable(subscription)
                .map(UserSubscription::getSubscriptionPlan)
                .map(SubscriptionPlan::getCode)
                .orElse(null);

        String formattedEndDate = null;
        if (subscription != null && subscription.getSubscriptionEndDate() != null) {
            formattedEndDate = DateTimeFormatter.ofPattern("dd MMMM yyyy")
                    .withZone(ZoneId.of(user.getTimeZone()))
                    .format(subscription.getSubscriptionEndDate());
        }

        boolean autoUpdate = Optional.ofNullable(subscription)
                .map(UserSubscription::isAutoUpdateEnabled)
                .orElse(true);

        return new UserProfileDTO(
                user.getEmail(),
                user.getTimeZone(),
                planCode,
                formattedEndDate,
                autoUpdate
        );
    }

}