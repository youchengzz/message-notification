package com.huixuan.ordertask.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.huixuan.ordertask.config.SignUtil;
import com.huixuan.ordertask.entity.LoginResponse;
import com.huixuan.ordertask.services.OrderMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OrderScheduledTask {

    private final RestTemplate restTemplate;

    private final OrderMonitorService orderMonitorService;

    /**
     * 定时规则：每分钟执行一次（cron表达式：秒 分 时 日 月 周）
     */
    //@Scheduled(cron = "0 0/1 * * * ?")
    public void executeOrderTask() {
        System.out.println("=====================================");
        System.out.println("⏰ 定时任务执行：" + System.currentTimeMillis());
        orderMonitorService.queryAndMonitorOrder();
        System.out.println("=====================================\n");
    }

    public static void main(String[] args) {
        long timestamp = Instant.now().toEpochMilli();
        // 校验：必须是2024年之后的时间，避免1970年错误
        if (timestamp < 1704067200000L) {
            throw new RuntimeException("本地时间错误！当前时间：" + timestamp + "，请同步系统时间");
        }
        String timestampStr = String.valueOf(timestamp);
        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Timestamp", timestampStr);
        headers.set("UserId", "gp2OPiuec4Ifbw0x1hrJ6GM5dFqDnEys");
        String signStr = timestampStr + "{}" + "MZeORVhfJjIE83DF1d4k0xQU2PrscCua";

        headers.set("Sign", SignUtil.sha1Encrypt(signStr));
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        // 调用登录接口
        JSONObject response = new RestTemplate().postForObject("https://shop.cardvip.cc/api/v1/user/info", entity, JSONObject.class);
        System.out.println(response);
    }
}
