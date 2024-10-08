package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.ConfirmationToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RandomlyGeneratedString randomlyGeneratedString;
    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final HtmlEmailTemplateService htmlEmailTemplateService;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       EmailService emailService, RandomlyGeneratedString randomlyGeneratedString,
                       ConfirmationTokenRepository confirmationTokenRepository, HtmlEmailTemplateService htmlEmailTemplateService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.randomlyGeneratedString = randomlyGeneratedString;
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.htmlEmailTemplateService = htmlEmailTemplateService;
    }

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

    public Optional<User> findByUser(String email) {
        return userRepository.findByEmail(email);
    }

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