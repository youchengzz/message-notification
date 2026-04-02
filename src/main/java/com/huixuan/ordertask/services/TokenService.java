package com.huixuan.ordertask.services;

import com.alibaba.fastjson2.JSONObject;
import com.huixuan.ordertask.config.SignUtil;
import com.huixuan.ordertask.entity.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final RestTemplate restTemplate;

    @Value("${app.api.login-url}")
    private String loginUrl;
    @Value("${app.api.username}")
    private String username;
    @Value("${app.api.password}")
    private String password;
    @Value("${app.api.user-id")
    private String userId;
    @Value("${app.api.api-key}")
    private String appKey;

    /**
     * 获取Token，缓存24小时
     */
    @Cacheable(value = "api-token", key = "'token'")
    public String getToken() {
        long timestamp = Instant.now().toEpochMilli();
        // 校验：必须是2024年之后的时间，避免1970年错误
        if (timestamp < 1704067200000L) {
            throw new RuntimeException("本地时间错误！当前时间：" + timestamp + "，请同步系统时间");
        }
        String timestampStr = String.valueOf(timestamp);
        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        System.out.println(timestampStr);
        headers.set("Timestamp", timestampStr);
        headers.set("UserId", userId);
        JSONObject param = new JSONObject();
        param.put("captchaType", "blockPuzzle");
        param.put("captchaVerification", "");
        param.put("password", password);
        param.put("username", username);
        String signStr = timestampStr + param.toJSONString() + appKey;
        // 构造请求参数
        headers.set("Sign", SignUtil.sha1Encrypt(signStr));
        HttpEntity<String> entity = new HttpEntity<>(param.toJSONString(), headers);

        // 调用登录接口
        LoginResponse response = restTemplate.postForObject(loginUrl, entity, LoginResponse.class);

        if (response == null || response.getCode() != 200) {
            throw new RuntimeException("登录失败：" + (response == null ? "接口无响应" : response.getMsg()));
        }
        return response.getData().getToken();
    }

}