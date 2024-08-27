package com.skyflytech.accountservice.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author pzr
 * @date:2024-08-20-5:47
 * @Description:
 **/
@Data
@Document(collection="users")
public class User {
    @Id
    private String id;
    private String username;

    private List<String> accountSetIds=new ArrayList<>();
    private String email;
    private String password;
}
