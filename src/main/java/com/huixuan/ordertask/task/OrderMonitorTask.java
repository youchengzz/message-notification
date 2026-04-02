package com.huixuan.ordertask.task;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.huixuan.ordertask.config.SignUtil;
import com.huixuan.ordertask.config.WechatConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderMonitorTask {

    @Value("${order.api-url}")
    private String orderApiUrl;

    @Value("${order.user-id}")
    private String userId;

    @Value("${order.api-key}")
    private String apiKey;

    private final WechatConfig wechatConfig;

    // 缓存监听订单 ordersn -> status
    private static final Map<String, Integer> orderCache = new ConcurrentHashMap<>();
    // 已推送去重
    private static final Set<String> pushedSet = new ConcurrentSkipListSet<>();

    // ====================== 每3秒查询：等待处理1、处理中2 ======================
    @Scheduled(fixedRate = 3000)
    public void queryNewOrder() {
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("limit", 10);
            param.put("page", 1);
            param.put("status", "2");

            String jsonBody = JSONUtil.toJsonStr(param);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String sign = SignUtil.sha1Encrypt(timestamp + jsonBody + apiKey);

            HttpResponse response = HttpRequest.post(orderApiUrl)
                    .header("UserId", userId)
                    .header("Sign", sign)
                    .header("Timestamp", timestamp)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .timeout(10000)
                    .execute();

            JSONObject res = JSONUtil.parseObj(response.body());
            if (!res.getInt("code", 0).equals(200)) {
                log.warn("订单接口返回异常:{}", res);
                return;
            }

            JSONArray data = res.getJSONArray("data");
            if (data.isEmpty()) {
                log.info("暂无待处理/处理中订单");
                return;
            }

            log.info("查询到待处理订单数:{}", data.size());
            for (int i = 0; i < data.size(); i++) {
                JSONObject order = data.getJSONObject(i);
                String ordersn = order.getStr("ordersn");
                Integer status = order.getInt("status");

                if (!pushedSet.contains(ordersn)) {
                    sendWechatTemplate(order, "新订单待处理");
                    pushedSet.add(ordersn);
                }
                orderCache.put(ordersn, status);
            }
        } catch (Exception e) {
            log.error("查询订单异常", e);
        }
    }

    // ====================== 每秒监听缓存订单状态 ======================
    @Scheduled(fixedRate = 1000)
    public void listenOrderStatus() {
        if (orderCache.isEmpty()) return;

        try {
            for (String ordersn : orderCache.keySet()) {
                Map<String, Object> param = MapUtil.<String, Object>builder()
                        .put("ordersn", ordersn)
                        .build();

                String timestamp = String.valueOf(System.currentTimeMillis());
                String jsonBody = JSONUtil.toJsonStr(param);
                String sign = SignUtil.sha1Encrypt(timestamp + jsonBody + apiKey);

                HttpResponse resp = HttpRequest.post(orderApiUrl)
                        .header("UserId", userId)
                        .header("Sign", sign)
                        .header("Timestamp", String.valueOf(timestamp))
                        .header("Content-Type", "application/json")
                        .body(jsonBody)
                        .execute();

                JSONObject res = JSONUtil.parseObj(resp.body());
                if (res.getInt("code", 0) != 200) continue;

                JSONArray data = res.getJSONArray("data");
                if (data.isEmpty()) continue;

                JSONObject order = data.getJSONObject(0);
                Integer newStatus = order.getInt("status");
                Integer oldStatus = orderCache.get(ordersn);

                // 状态变为 3成功 / 4取消
                if (!newStatus.equals(oldStatus) && (newStatus == 3 || newStatus == 4)) {
                    sendWechatTemplate(order, "订单状态已更新");
                    orderCache.remove(ordersn);
                    log.info("订单{}状态变更，移出监听", ordersn);
                }
            }
        } catch (Exception e) {
            // 不中断
        }
    }

    // ====================== 发送微信模板消息 ======================
    private void sendWechatTemplate(JSONObject order, String first) {
        try {
            // 1. 获取 access_token
            String tokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + wechatConfig.getAppId()
                    + "&secret=" + wechatConfig.getAppSecret();

            JSONObject tokenRes = JSONUtil.parseObj(HttpRequest.get(tokenUrl).execute().body());
            String accessToken = tokenRes.getStr("access_token");

            // 2. 组装模板消息
            String url = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + accessToken;

            Map<String, Object> data = new HashMap<>();
            data.put("touser", wechatConfig.getOpenId());
            data.put("template_id", wechatConfig.getTemplateId());

            Map<String, Object> keywords = new HashMap<>();
            keywords.put("first", MapUtil.of("value", first));
            keywords.put("keyword1", MapUtil.of("value", order.getStr("ordersn")));
            keywords.put("keyword2", MapUtil.of("value", order.getStr("goods_name")));
            keywords.put("keyword3", MapUtil.of("value", getStatusText(order.getInt("status"))));
            keywords.put("keyword4", MapUtil.of("value", order.getStr("recharge_account")));
            keywords.put("remark", MapUtil.of("value", "系统自动监控通知"));

            data.put("data", keywords);
            String body = JSONUtil.toJsonStr(data);

            HttpRequest.post(url).body(body).execute();
            log.info("微信模板消息推送成功 ordersn:{}", order.getStr("ordersn"));
        } catch (Exception e) {
            log.error("推送微信失败", e);
        }
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case -1: return "未支付";
            case 1: return "等待处理";
            case 2: return "正在处理";
            case 3: return "交易成功";
            case 4: return "取消交易";
            case 5: return "已退款";
            default: return "未知状态";
        }
    }
}
