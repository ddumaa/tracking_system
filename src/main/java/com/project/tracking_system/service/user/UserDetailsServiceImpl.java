package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Реализация сервиса для загрузки деталей пользователя для аутентификации.
 * <p>
 * Этот сервис реализует интерфейс {@link UserDetailsService} и используется для получения информации о пользователе
 * по его email для аутентификации в рамках Spring Security.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Загружает данные пользователя по его email.
     * <p>
     * Метод ищет пользователя в базе данных по email. Если пользователь не найден,
     * выбрасывается исключение {@link UsernameNotFoundException}.
     * </p>
     *
     * @param email Email пользователя, для которого нужно загрузить детали.
     * @return {@link UserDetails} с информацией о пользователе.
     * @throws UsernameNotFoundException Если пользователь с указанным email не найден.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Такой пользователь не найден: " + email));
    }
}