package com.kungeek.seleniumplus.pool.util;

import java.util.UUID;

public class UUIDUtil {
    /**
     * 获取32小写随机字符串
     */
    public static String get32LowStr() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    /**
     * 获取32位大写字符串
     */
    public static String get32HighStr() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 获取32位字符串
     */
    public static String get32CommonStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
