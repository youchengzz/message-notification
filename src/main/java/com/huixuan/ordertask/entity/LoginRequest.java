package com.huixuan.ordertask.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;
    private String captchaType = "blockPuzzle";
    private String captchaVerification = "";
}

