package com.huixuan.ordertask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 使用 忽略所有SSL证书校验 的OkHttp
        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(SslUtil.getUnsafeOkHttpClient());
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}