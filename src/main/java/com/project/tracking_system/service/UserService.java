package com.project.tracking_system.service;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UsernameAlreadyExistsException;
import com.project.tracking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Hello
 */
@Service
public class UserService {

    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserDetailsServiceImpl userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    public Optional<User> add(UserRegistrationDTO userDTO) {
        if (userDTO.getUsername() == null || userDTO.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        if (userRepository.findByUsername(userDTO.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Имя пользователя уже существует, пожалуйста, выберите другое");
        }
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        try {
            return Optional.of(userRepository.save(user));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsernameAndPassword(UserRegistrationDTO userDTO) {

        Optional<User> byUsername = userRepository.findByUsername(userDTO.getUsername());
        if (byUsername.isPresent()) {
            User user = byUsername.get();
            if (passwordEncoder.matches(userDTO.getPassword(), user.getPassword())) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public void autoLogin(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

}
