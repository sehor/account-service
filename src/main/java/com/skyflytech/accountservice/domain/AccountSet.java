package com.skyflytech.accountservice.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * @Author pzr
 * @date:2024-08-20-5:40
 * @Description:
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "account_sets")
public class AccountSet {
    @Id
    private String id;

    @NotBlank(message = "账套名称不能为空")
    @Indexed(unique = true)
    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ... 其他字段和方法 ...
}