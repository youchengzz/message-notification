package com.huixuan.ordertask.services;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class WechatPushService {
    private final RestTemplate restTemplate;

    @Value("${app.wechat.app-id}")
    private String appId;
    @Value("${app.wechat.app-secret}")
    private String appSecret;
    @Value("${app.wechat.open-id}")
    private String openId;
    @Value("${app.wechat.template-id}")
    private String templateId;

    /**
     * 推送订单状态变更消息
     */
    public void pushOrderStatusChange(String orderSn, String oldStatus, String newStatus, String goodsName) {
        try {
            // 1. 获取微信access_token
            String tokenUrl = String.format(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                    appId, appSecret
            );
            JSONObject tokenResp = restTemplate.getForObject(tokenUrl, JSONObject.class);
            String accessToken = tokenResp.getString("access_token");

            // 2. 构造模板消息
            JSONObject message = new JSONObject();
            message.put("touser", openId);
            message.put("template_id", templateId);

            JSONObject data = new JSONObject();
            data.put("first", JSONObject.of("value", "订单状态已变更"));
            data.put("keyword1", JSONObject.of("value", orderSn));
            data.put("keyword2", JSONObject.of("value", oldStatus + " → " + newStatus));
            data.put("keyword3", JSONObject.of("value", goodsName));
            data.put("remark", JSONObject.of("value", "请及时处理"));

            message.put("data", data);

            // 3. 发送推送
            String pushUrl = String.format(
                    "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s",
                    accessToken
            );
            restTemplate.postForObject(pushUrl, message, String.class);

            System.out.println("✅ 微信推送成功：订单号=" + orderSn);
        } catch (Exception e) {
            System.err.println("❌ 微信推送失败：" + e.getMessage());
        }
    }

}
