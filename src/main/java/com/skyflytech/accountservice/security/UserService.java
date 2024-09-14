package com.skyflytech.accountservice.security;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final int CACHE_SIZE = 500; // 设置缓存大小
    private final Map<String, User> userCache = new LinkedHashMap<String, User>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, User> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(); // 在这里创建新的 PasswordEncoder
    }

    public User registerUser(User user) {
        // 检查用户名和邮箱是否已存在
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if(user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 保存用户
        User savedUser = userRepository.save(user);
        synchronized (userCache) {
            userCache.put(savedUser.getUsername(), savedUser);
        }
        return savedUser;
    }

    public User getUserByUsername(String username) throws UsernameNotFoundException {
        synchronized (userCache) {
            return userCache.computeIfAbsent(username, this::loadUserFromDatabase);
        }
    }

    private User loadUserFromDatabase(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("User not found: " + username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        logger.info("User found: " + username);
        return user;
    }

    public User updateUserCurrentAccountSetId(String username, String newAccountSetId) throws UsernameNotFoundException {
        User user = getUserByUsername(username);
        user.setCurrentAccountSetId(newAccountSetId);
        User updatedUser = userRepository.save(user);
        synchronized (userCache) {
            userCache.put(updatedUser.getUsername(), updatedUser);
        }
        return updatedUser;
    }

    public User saveUser(User user) {
        User savedUser = userRepository.save(user);
        synchronized (userCache) {
            userCache.put(savedUser.getUsername(), savedUser);
        }
        return savedUser;
    }

    public void deleteUser(User user) {
        userRepository.delete(user);
        synchronized (userCache) {
            userCache.remove(user.getUsername());
        }
    }

    public void deleteUser(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        deleteUser(user);
    }
}