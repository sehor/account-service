package com.skyflytech.accountservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

/**
 * @Author pzr
 * @date:2024-08-20-5:47
 * @Description:
 **/
@Data
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String username;
    private String password;
    private String email;
    // 其他需要的字段
}
