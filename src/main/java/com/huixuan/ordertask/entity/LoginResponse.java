package com.huixuan.ordertask.entity;

import lombok.Data; /**
 * 登录响应
 */
@Data
public class LoginResponse {
    private Integer code;
    private String msg;
    private LoginData data;

    @Data
    public static class LoginData {
        private String token;
    }
}
