package com.skyflytech.accountservice.security.service;

import com.skyflytech.accountservice.security.model.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

/**
 * @Author pzr
 * @date:2024-09-28-7:56
 * @Description:
 **/
public interface UserService {

    public User registerUser(User user);


    public User getUserByUsername(String username) throws UsernameNotFoundException ;




    public User updateUserCurrentAccountSetId(String username, String newAccountSetId) throws UsernameNotFoundException ;

    //update user accountSetIds

    public User updateUserAccountSetIds(String username, List<String> accountSetIds) throws UsernameNotFoundException ;


    public User saveUser(User user);


    public void deleteUser(User user) ;



    public void deleteUser(String id) ;
}
