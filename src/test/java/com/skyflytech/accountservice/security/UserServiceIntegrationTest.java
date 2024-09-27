package com.skyflytech.accountservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(User.class);
        cacheManager.getCache("users").clear();
    }

    @Test
    void testUserCaching() {
        // 创建并保存用户
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        user.setEmail("test@example.com");
        User savedUser = userService.registerUser(user);

        // 验证用户被保存到数据库
        assertNotNull(savedUser.getId());

        // 第一次获取用户，应该从数据库加载并缓存
        User fetchedUser1 = userService.getUserByUsername("testuser");
        assertEquals(savedUser.getId(), fetchedUser1.getId());

        // 验证用户被缓存
        assertNotNull(cacheManager.getCache("users").get("testuser"));

        // 第二次获取用户，应该从缓存中获取
        User fetchedUser2 = userService.getUserByUsername("testuser");
        assertEquals(savedUser.getId(), fetchedUser2.getId());

        // 更新用户，应该更新缓存
        User updatedUser = userService.updateUserCurrentAccountSetId("testuser", "newAccountSetId");
        assertEquals("newAccountSetId", updatedUser.getCurrentAccountSetId());

        // 验证缓存被更新
        User cachedUser = (User) cacheManager.getCache("users").get("testuser").get();
        assertEquals("newAccountSetId", cachedUser.getCurrentAccountSetId());

        // 再次获取用户，应该从更新后的缓存中获取
        User fetchedUser3 = userService.getUserByUsername("testuser");
        assertEquals("newAccountSetId", fetchedUser3.getCurrentAccountSetId());

        // 删除用户，应该清除缓存
         userService.deleteUser(fetchedUser3);

        // 验证缓存被清除
        assertNull(cacheManager.getCache("users").get("testuser"));

        // 尝试再次获取用户，应该抛出异常
        assertThrows(UsernameNotFoundException.class, () -> userService.getUserByUsername("testuser"));
    }
}