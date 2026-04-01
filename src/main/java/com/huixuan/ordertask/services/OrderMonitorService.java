package com.huixuan.ordertask.services;

import com.alibaba.fastjson2.JSONObject;
import com.huixuan.ordertask.entity.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单查询 + 状态监控
 */
@Service
@RequiredArgsConstructor
public class OrderMonitorService {

    private final RestTemplate restTemplate;
    private final TokenService tokenService;
    private final WechatPushService wechatPushService;

    @Value("${app.api.order-url}")
    private String orderUrl;
    @Value("${app.order.wait-status}")
    private Integer waitStatus;
    @Value("${app.order.processing-status}")
    private Integer processingStatus;

    // 线程安全缓存：存储上一次的订单状态（订单号 -> 状态码）
    private final Map<String, Integer> orderStatusCache = new ConcurrentHashMap<>();

    /**
     * 查询最近1分钟订单 + 监控状态变化
     */
    public void queryAndMonitorOrder() {
        try {
            // 1. 构造时间参数：最近1分钟（ISO 8601格式）
            Instant endTime = Instant.now();
            Instant startTime = endTime.minusSeconds(60);
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

            // 2. 构造请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("create_time[]", formatter.format(startTime));
            params.add("create_time[]", formatter.format(endTime));
            params.add("page", "1");
            params.add("limit", "100");

            // 3. 构造请求头（带Token）
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + tokenService.getToken());

            // 4. 调用订单接口
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            JSONObject response = restTemplate.exchange(
                    orderUrl, HttpMethod.GET, entity, JSONObject.class
            ).getBody();

            if (response == null || !response.containsKey("list")) {
                System.err.println("❌ 订单接口无数据");
                return;
            }

            // 5. 解析订单列表
            OrderResponse orderResp = response.to(OrderResponse.class);
            System.out.println("📊 本次查询到订单数：" + orderResp.getTotal());

            // 6. 过滤：只保留 等待处理/正在处理 的订单
            orderResp.getList().forEach(order -> {
                Integer status = order.getStatus();
                String orderSn = order.getOrdersn();

                if (!status.equals(waitStatus) && !status.equals(processingStatus)) {
                    return;
                }

                // 7. 状态对比：状态变化则推送微信
                Integer oldStatus = orderStatusCache.get(orderSn);
                if (oldStatus != null && !oldStatus.equals(status)) {
                    // 状态变更：推送消息
                    wechatPushService.pushOrderStatusChange(
                            orderSn,
                            getStatusText(oldStatus),
                            order.getStatus_text(),
                            order.getGoods_name()
                    );
                }

                // 8. 更新缓存
                orderStatusCache.put(orderSn, status);
            });

        } catch (Exception e) {
            System.err.println("❌ 订单查询失败：" + e.getMessage());
        }
    }

    /**
     * 状态码转文案
     */
    private String getStatusText(Integer status) {
        if (status.equals(waitStatus)) return "等待处理";
        if (status.equals(processingStatus)) return "正在处理";
        return "未知状态";
    }
}