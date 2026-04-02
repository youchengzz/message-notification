package com.huixuan.ordertask.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 功能描述
 *
 * @author: wpeng
 * @date: 2026年04月02日 10:19
 */
public class SignUtil {

    public static String sha1Encrypt(String data) {
        System.out.println("待加密数据：" + data);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append(0);
                }
                sb.append(hex);
            }
            return sb.toString(); // 输出：小写40位SHA1结果
        } catch (Exception e) {
            throw new RuntimeException("SHA-1加密失败", e);
        }
    }

}
