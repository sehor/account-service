package com.skyflytech.accountservice.security.model;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Data;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Data
@Document(collection = "users")
public class User implements UserDetails{
    @Id
    private String id;
    private String username;
    private String password;
    private String email;
    private String currentAccountSetId;
    private List<String> accountSetIds=new ArrayList<>();
    // 其他需要的字段

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}