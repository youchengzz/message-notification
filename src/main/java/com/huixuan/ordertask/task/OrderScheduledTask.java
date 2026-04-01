package com.huixuan.ordertask.task;

import com.huixuan.ordertask.services.OrderMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderScheduledTask {

    private final OrderMonitorService orderMonitorService;

    /**
     * 定时规则：每分钟执行一次（cron表达式：秒 分 时 日 月 周）
     */
    @Scheduled(cron = "0 1 * * * ?")
    public void executeOrderTask() {
        System.out.println("=====================================");
        System.out.println("⏰ 定时任务执行：" + System.currentTimeMillis());
        orderMonitorService.queryAndMonitorOrder();
        System.out.println("=====================================\n");
    }
}
