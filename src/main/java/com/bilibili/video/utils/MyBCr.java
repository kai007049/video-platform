package com.bilibili.video.utils;

import org.mindrot.jbcrypt.BCrypt;

public class MyBCr {

    // 加密密码
    public static String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    // 校验密码
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}