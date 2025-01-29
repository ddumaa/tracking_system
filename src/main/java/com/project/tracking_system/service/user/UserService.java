package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.ConfirmationToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.utils.EncryptionUtils;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

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
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RandomlyGeneratedString randomlyGeneratedString;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final HtmlEmailTemplateService htmlEmailTemplateService;
    private final EncryptionUtils encryptionUtils;
    private final JwtTokenManager jwtTokenManager;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       EmailService emailService, RandomlyGeneratedString randomlyGeneratedString,
                       ConfirmationTokenRepository confirmationTokenRepository, HtmlEmailTemplateService htmlEmailTemplateService,
                       EncryptionUtils encryptionUtils, JwtTokenManager jwtTokenManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.randomlyGeneratedString = randomlyGeneratedString;
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.htmlEmailTemplateService = htmlEmailTemplateService;
        this.encryptionUtils = encryptionUtils;
        this.jwtTokenManager = jwtTokenManager;
    }

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
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует.");
        }

        String confirmationCode = randomlyGeneratedString.generateConfirmCodRegistration();

        String emailContent = htmlEmailTemplateService.generateConfirmationEmail(confirmationCode);

        Optional<ConfirmationToken> existingToken = confirmationTokenRepository.findByEmail(userDTO.getEmail());

        if (existingToken.isPresent()) {
            // Если токен существует, обновляем код подтверждения и время создания
            ConfirmationToken token = existingToken.get();
            token.setConfirmationCode(confirmationCode);
            token.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            confirmationTokenRepository.save(token);
        } else {
            // Если токена нет, создаем новый
            ConfirmationToken token = new ConfirmationToken(userDTO.getEmail(), confirmationCode);
            confirmationTokenRepository.save(token);
        }

        try {
            emailService.sendHtmlEmail(userDTO.getEmail(), "Подтверждение регистрации", emailContent);
        } catch (MessagingException e) {
            throw new RuntimeException("Ошибка при отправке email", e);
        }
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
        Optional<ConfirmationToken> optionalToken = confirmationTokenRepository.findByEmail(userDTO.getEmail());

        if (optionalToken.isPresent()) {
            ConfirmationToken token = optionalToken.get();

            if (token.getConfirmationCode().equals(userDTO.getConfirmCodRegistration())) {

                ZonedDateTime tokenCreatedAt = token.getCreatedAt();
                ZonedDateTime oneHourAgoUtc = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);

                if (tokenCreatedAt.isBefore(oneHourAgoUtc)) {
                    throw new IllegalArgumentException("Срок действия кода подтверждения истек");
                }

                User user = new User();
                user.setEmail(userDTO.getEmail());
                user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
                userRepository.save(user);

                confirmationTokenRepository.deleteByEmail(userDTO.getEmail());
            } else {
                throw new IllegalArgumentException("Неверный код подтверждения");
            }
        } else {
            throw new IllegalArgumentException("Код подтверждения не найден");
        }
    }

    public void updateEvropostCredentialsAndSettings(String email, EvropostCredentialsDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        try {
            // Шифрование пароля и номера сервиса
            String encryptedPassword = encryptionUtils.encrypt(dto.getEvropostPassword());
            String encryptedServiceNumber = encryptionUtils.encrypt(dto.getServiceNumber());

            // Обновление всех данных
            user.setEvropostUsername(dto.getEvropostUsername());
            user.setEvropostPassword(encryptedPassword);
            user.setServiceNumber(encryptedServiceNumber);
            user.setUseCustomCredentials(dto.getUseCustomCredentials());

            userRepository.save(user);

            String newToken = jwtTokenManager.getUserToken(user);
            if (newToken == null) {
                throw new RuntimeException("Не удалось создать JWT токен");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при шифровании данных", e);
        }
    }

    public void updateUseCustomCredentials(String email, Boolean useCustomCredentials) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setUseCustomCredentials(useCustomCredentials);

        // Логирование изменений
        logger.info("Обновление useCustomCredentials для пользователя {}: {}", email, useCustomCredentials);

        userRepository.save(user);
    }


    public EvropostCredentialsDTO getEvropostCredentials(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        try {
            // Расшифровка данных
            String decryptedPassword = encryptionUtils.decrypt(user.getEvropostPassword());
            String decryptedServiceNumber = encryptionUtils.decrypt(user.getServiceNumber());

            // Возврат DTO с расшифрованными данными
            EvropostCredentialsDTO dto = new EvropostCredentialsDTO();
            dto.setEvropostUsername(user.getEvropostUsername());
            dto.setEvropostPassword(decryptedPassword);
            dto.setServiceNumber(decryptedServiceNumber);
            dto.setUseCustomCredentials(user.getUseCustomCredentials());

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при расшифровке данных", e);
        }
    }

    /**
     * Находит пользователя по его email.
     *
     * @param email Email пользователя.
     * @return Опциональный объект с пользователем.
     */
    public Optional<User> findByUser(String email) {
        return userRepository.findByEmail(email);
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
    public void changePassword(String email, UserSettingsDTO userSettingsDTO) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (passwordEncoder.matches(userSettingsDTO.getCurrentPassword(), user.getPassword())) {
                user.setPassword(passwordEncoder.encode(userSettingsDTO.getNewPassword()));
                userRepository.save(user);
            } else {
                throw new IllegalArgumentException("Текущий пароль введён неверно");
            }
        } else {
            throw new IllegalArgumentException("Пользователь не найден");
        }
    }

    /**
     * Удаляет текущего пользователя.
     * <p>
     * Метод удаляет пользователя из базы данных на основе данных текущей аутентификации.
     * </p>
     */
    public void deleteUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            userRepository.delete(user);
        }
    }
}