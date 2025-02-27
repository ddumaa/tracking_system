package com.project.tracking_system.service.user;

import com.project.tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


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
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Загружает данные пользователя по его email.
     *
     * @param email Email пользователя.
     * @return {@link UserDetails} с информацией о пользователе.
     * @throws UsernameNotFoundException Если пользователь не найден.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Такой пользователь не найден: " + email));
    }
}