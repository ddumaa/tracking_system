package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.ConfirmationToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.utils.EncryptionUtils;
import com.project.tracking_system.utils.UserCredentialsResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RandomlyGeneratedString randomlyGeneratedString;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final EncryptionUtils encryptionUtils;
    private final JwtTokenManager jwtTokenManager;
    private final UserCredentialsResolver userCredentialsResolver;

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

        if (isEmailAlreadyRegistered(email)) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует.");
        }

        String confirmationCode = randomlyGeneratedString.generateConfirmCodRegistration();
        saveOrUpdateConfirmationToken(email, confirmationCode);

        // Отправка email в фоне (не блокируем основной поток)
        log.info("Отправка email с кодом подтверждения на: {}", email);
        emailService.sendConfirmationEmail(email, confirmationCode);

        log.info("Код подтверждения отправлен на email: {}", email);
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
                            log.info("Обновлен код подтверждения для email: {}", email);
                        },
                        () -> {
                            ConfirmationToken newToken = new ConfirmationToken(email, confirmationCode);
                            confirmationTokenRepository.save(newToken);
                            log.info("Создан новый код подтверждения для email: {}", email);
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
        ConfirmationToken token = confirmationTokenRepository.findByEmail(userDTO.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Код подтверждения не найден"));

        ZonedDateTime tokenCreatedAt = token.getCreatedAt();
        ZonedDateTime oneHourAgoUtc = ZonedDateTime.now(UTC).minusHours(1);

        if (tokenCreatedAt.isBefore(oneHourAgoUtc)) {
            log.warn("Код подтверждения для email {} истек", userDTO.getEmail());
            throw new IllegalArgumentException("Срок действия кода подтверждения истек");
        }

        if (!token.getConfirmationCode().equals(userDTO.getConfirmCodRegistration())) {
            log.warn("Неверный код подтверждения для email {}", userDTO.getEmail());
            throw new IllegalArgumentException("Неверный код подтверждения");
        }

        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRoles(Set.of(Role.ROLE_USER));
        userRepository.save(user);

        confirmationTokenRepository.deleteByEmail(userDTO.getEmail());

        log.info("Регистрация пользователя {} завершена. Код подтверждения удален.", userDTO.getEmail());
    }

    public void updateUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        try {
            Role role = Role.valueOf(newRole); // Проверяем, что роль существует

            // Если у пользователя уже есть такая роль, ничего не делаем
            if (user.getRoles().contains(role)) {
                log.info("Роль пользователя с ID {} уже установлена на {}", userId, newRole);
                return;
            }

            user.getRoles().clear(); // Полностью заменяем роли пользователя
            user.getRoles().add(role);
            userRepository.save(user);

            log.info("Роль пользователя с ID {} успешно обновлена до {}", userId, newRole);
        } catch (IllegalArgumentException e) {
            log.error("Ошибка обновления роли: Некорректное значение '{}'", newRole, e);
            throw new IllegalArgumentException("Некорректная роль: " + newRole);
        }
    }

    public void updateEvropostCredentialsAndSettings(Long userId, EvropostCredentialsDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        try {
            // Шифрование пароля и номера сервиса
            String encryptedPassword = encryptionUtils.encrypt(dto.getEvropostPassword());
            String encryptedServiceNumber = encryptionUtils.encrypt(dto.getServiceNumber());

            // Логируем обновление данных перед сохранением
            log.info("Обновление учетных данных Evropost для пользователя с ID: {}", userId);

            // Обновление всех данных
            user.setEvropostUsername(dto.getEvropostUsername());
            user.setEvropostPassword(encryptedPassword);
            user.setServiceNumber(encryptedServiceNumber);
            user.setUseCustomCredentials(dto.getUseCustomCredentials());

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

    public void updateUseCustomCredentials(Long userId, Boolean useCustomCredentials) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        user.setUseCustomCredentials(useCustomCredentials);
        userRepository.save(user);

        log.info("Флаг 'useCustomCredentials' обновлён для пользователя с ID {}: {}", userId, useCustomCredentials);
    }

    public EvropostCredentialsDTO getEvropostCredentials(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        EvropostCredentialsDTO dto = new EvropostCredentialsDTO();
        dto.setEvropostUsername(user.getEvropostUsername());
        dto.setUseCustomCredentials(user.getUseCustomCredentials());

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

    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public long countUsers() {
        return userRepository.count();
    }

    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userRepository.incrementUpdateCount(userId, count, ZonedDateTime.now(ZoneOffset.UTC));
    }

    public ResolvedCredentialsDTO resolveCredentials(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));
        return userCredentialsResolver.resolveCredentials(user);
    }

    public boolean isUsingCustomCredentials(Long userId) {
        return userRepository.isUsingCustomCredentials(userId);
    }

    public long countUsersBySubscriptionPlan(String planName) {
        return userRepository.countUsersBySubscriptionPlan(planName);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден с ID: " + userId));

        userRepository.delete(user);
        log.info("Пользователь с ID {} был удален.", userId);
    }

}