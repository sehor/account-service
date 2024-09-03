package com.skyflytech.accountservice.utils;

import java.util.Collection;
import java.util.Objects;

/**
 * @Author pzr
 * @date:2024-08-19-7:57
 * @Description:
 **/
public class Utils {

    public static <T> boolean isNotNullOrEmpty(Collection<T> list) {
        return Objects.nonNull(list) && !list.isEmpty();
    }
    public static boolean isNotEmpty(String s){
        return s!=null&&!s.isEmpty();
    }

}
