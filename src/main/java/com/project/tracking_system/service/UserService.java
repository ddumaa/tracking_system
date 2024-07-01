package com.project.tracking_system.service;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void save(UserRegistrationDTO userDTO) {
        if (userDTO.getEmail() == null || userDTO.getEmail().isEmpty()) {
            throw new IllegalArgumentException("Введите электронную почту");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Данная почта уже используется," +
                    " войдите в свой аккаунт или используйте другую электронную почту");
        }
        if (!userDTO.getPassword().equals(userDTO.getConfirmPassword())){
            throw new IllegalArgumentException("Введенные пароли не совпадают");
        }
        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

}
