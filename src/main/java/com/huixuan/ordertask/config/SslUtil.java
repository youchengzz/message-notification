package com.huixuan.ordertask.config;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import okhttp3.OkHttpClient;

public class SslUtil {

    // 获取 忽略所有SSL校验 的OkHttp客户端
    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // 忽略证书校验
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 初始化SSL上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // 创建OkHttp客户端
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}