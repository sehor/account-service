package com.skyflytech.accountservice.security;

public class UserContextHolder {
    private static final ThreadLocal<String> currentAccountSetId = new ThreadLocal<>();

    public static void setCurrentAccountSetId(String accountSetId) {
        currentAccountSetId.set(accountSetId);
    }

    public static String getCurrentAccountSetId() {
        return currentAccountSetId.get();
    }

    public static void clear() {
        currentAccountSetId.remove();
    }
}