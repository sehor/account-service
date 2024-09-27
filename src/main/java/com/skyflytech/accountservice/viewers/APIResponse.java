package com.skyflytech.accountservice.viewers;

import lombok.Data;

@Data
public class APIResponse<T> {
    private String message;
    private T data;

    public APIResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

}
