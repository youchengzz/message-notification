package com.huixuan.ordertask.services;

import com.alibaba.fastjson2.JSON;
import com.huixuan.ordertask.entity.LoginRequest;
import com.huixuan.ordertask.entity.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    /**
     * 获取Token，缓存24小时
     */
    @Cacheable(value = "api-token", key = "'token'")
    public String getToken() {
        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构造请求参数
        LoginRequest request = new LoginRequest(username, password, "blockPuzzle", "");
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        // 调用登录接口
        LoginResponse response = restTemplate.postForObject(loginUrl, entity, LoginResponse.class);

        if (response == null || response.getCode() != 200) {
            throw new RuntimeException("登录失败：" + (response == null ? "接口无响应" : response.getMsg()));
        }
        return response.getData().getToken();
    }
}