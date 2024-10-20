package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.LoginAttempt;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.LoginAttemptRepository;
import com.project.tracking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 4;
    private static final long LOCK_TIME_DURATION = 1;

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    @Autowired
    public LoginAttemptService(UserRepository userRepository, LoginAttemptRepository loginAttemptRepository) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    public void loginSucceeded(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null) {
                loginAttempt.setAttempts(0);
                loginAttemptRepository.save(loginAttempt);
            }
        }
    }

    public boolean isBlocked(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null && loginAttempt.getAttempts() >= MAX_ATTEMPTS) {
                ZonedDateTime zonedDateTime = loginAttempt.getLastModified().plusHours(LOCK_TIME_DURATION);
                if (zonedDateTime.isAfter(ZonedDateTime.now(ZoneOffset.UTC))) {
                    return true;
                } else {
                    loginAttempt.setAttempts(0);
                    loginAttemptRepository.save(loginAttempt);
                }
            }
        }
        return false;
    }

    public void loginFailed(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt == null) {
                loginAttempt = new LoginAttempt();
                loginAttempt.setUser(user);
                user.setLoginAttempt(loginAttempt);
            }
            loginAttempt.setAttempts(loginAttempt.getAttempts() + 1);
            loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC));
            loginAttemptRepository.save(loginAttempt);
        }
    }

    public int getRemainingAttempts(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null) {
                return MAX_ATTEMPTS - loginAttempt.getAttempts();
            }
        }
        return MAX_ATTEMPTS;
    }

    public ZonedDateTime getUnlockTime(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null && loginAttempt.getAttempts() >= MAX_ATTEMPTS) {
                return loginAttempt.getLastModified().plusHours(LOCK_TIME_DURATION);
            }
        }
        return null;
    }
}