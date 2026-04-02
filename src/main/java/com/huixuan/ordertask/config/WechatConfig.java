package com.huixuan.ordertask.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wechat")
@Data
public class WechatConfig {
    private String appId;
    private String appSecret;
    private String templateId;
    private String openId;
}