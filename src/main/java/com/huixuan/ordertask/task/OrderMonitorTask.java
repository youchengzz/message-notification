package com.huixuan.ordertask.task;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.date.DateUtil;
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

    private static final TimedCache<String, String> ACCESS_TOKEN_CACHE = CacheUtil.newTimedCache(7000 * 1000);
    // ====================== 每3秒查询：等待处理1、处理中2 ======================
    @Scheduled(fixedRate = 2000)
    public void queryNewOrder() {
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("limit", 10);
            param.put("page", 1);
            param.put("status", "1,2");

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
                log.info("订单详情：{}", order.toString());
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
                        .header("Timestamp", timestamp)
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
    private void sendWechatTemplate(JSONObject order, String notifyType) {
        try {
            // 1. 从缓存获取AccessToken（避免频繁调用被限制）
            String accessToken = ACCESS_TOKEN_CACHE.get("WECHAT_ACCESS_TOKEN");
            if (accessToken == null) {
                String tokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                        + "&appid=" + wechatConfig.getAppId()
                        + "&secret=" + wechatConfig.getAppSecret();
                String tokenResp = HttpRequest.get(tokenUrl).timeout(10000).execute().body();
                JSONObject tokenRes = JSONUtil.parseObj(tokenResp);

                // 打印获取Token失败原因，方便排查
                if (tokenRes.containsKey("errcode") && tokenRes.getInt("errcode") != 0) {
                    log.error("❌ 获取AccessToken失败：{}", tokenResp);
                    return;
                }

                accessToken = tokenRes.getStr("access_token");
                ACCESS_TOKEN_CACHE.put("WECHAT_ACCESS_TOKEN", accessToken);
                log.info("✅ 刷新AccessToken成功");
            }

            // 2. 微信模板消息推送接口
            String pushUrl = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + accessToken;

            // 3. 组装模板消息（严格匹配模板字段！）
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("touser", wechatConfig.getOpenId());
            pushData.put("template_id", wechatConfig.getTemplateId());
            // 👇 可选：添加跳转链接（对应模板里的「点击查看详情」）
            pushData.put("url", "https://shop.cardvip.cc/pages/buyer/order");

            // 模板字段必须和{{xxx.DATA}}完全一致，大小写错一个就失败！
            Map<String, Object> templateData = new HashMap<>();
            // 产品名称 → {{thing7.DATA}}
            templateData.put("thing7", MapUtil.of("value", order.getStr("goods_name")));
            // 订单编号 → {{character_string10.DATA}}
            templateData.put("character_string10", MapUtil.of("value", order.getStr("ordersn")));
            // 订单金额 → {{amount9.DATA}}（加「元」后缀，符合展示习惯）
            templateData.put("amount9", MapUtil.of("value", order.getStr("total_price") + "元"));
            // 订单状态 → {{thing1.DATA}}
            templateData.put("thing1", MapUtil.of("value", getStatusText(order.getInt("status"))));
            // 下单时间 → {{time13.DATA}}（订单接口无下单时间，用当前系统时间，可按需修改）
            templateData.put("time13", MapUtil.of("value", DateUtil.now()));

            pushData.put("data", templateData);
            String body = JSONUtil.toJsonStr(pushData);

            // 4. 发送请求并打印完整响应（排查问题核心）
            String pushResp = HttpRequest.post(pushUrl)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .timeout(10000)
                    .execute().body();

            log.info("📩 微信推送完整响应：{}", pushResp);

            // 5. 判断推送结果
            JSONObject result = JSONUtil.parseObj(pushResp);
            if (result.getInt("errcode", -1) == 0) {
                log.info("✅ 微信模板消息推送成功！订单号：{}", order.getStr("ordersn"));
            } else {
                log.error("❌ 微信推送失败！错误信息：{}", pushResp);
            }

        } catch (Exception e) {
            log.error("❌ 推送微信异常", e);
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
