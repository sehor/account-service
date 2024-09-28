package com.skyflytech.accountservice.security.service.imp;

import com.skyflytech.accountservice.security.model.User;
import com.skyflytech.accountservice.security.repository.UserRepository;
import com.skyflytech.accountservice.security.service.UserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
@Service
public class UserServiceImp implements UserService
{
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImp.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public UserServiceImp(UserRepository userRepository) {
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
        return savedUser;
    }

    @Cacheable(value = "users", key = "#username")
    public User getUserByUsername(String username) throws UsernameNotFoundException {
        return loadUserFromDatabase(username);
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

    @CachePut(value = "users", key = "#username")
    public User updateUserCurrentAccountSetId(String username, String newAccountSetId) throws UsernameNotFoundException {
        User user = getUserByUsername(username);
        user.setCurrentAccountSetId(newAccountSetId);
        return userRepository.save(user);
    }

    //update user accountSetIds
    @CachePut(value = "users", key = "#username")
    public User updateUserAccountSetIds(String username, List<String> accountSetIds) throws UsernameNotFoundException {
        User user = getUserByUsername(username);
        user.setAccountSetIds(accountSetIds);
        return userRepository.save(user);
    }   

    @CachePut(value = "users", key = "#user.username")
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#user.username")
    public void deleteUser(User user) {
        userRepository.delete(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        deleteUser(user);
    }
}