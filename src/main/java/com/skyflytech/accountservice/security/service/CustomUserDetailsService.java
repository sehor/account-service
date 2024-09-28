package com.skyflytech.accountservice.security.service;

import com.skyflytech.accountservice.security.service.imp.UserServiceImp;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserServiceImp userServiceImp;

    public CustomUserDetailsService(UserServiceImp userServiceImp) {
        this.userServiceImp = userServiceImp;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userServiceImp.getUserByUsername(username);
    }
}