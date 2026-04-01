package com.huixuan.ordertask.entity;

import lombok.Data;

import java.util.List;

@Data
public class OrderResponse {
    private List<Order> list;
    private Integer total;

    /**
     * 订单实体
     */
    @Data
    public static class Order {
        private String ordersn;      // 订单号
        private Integer status;      // 订单状态
        private String status_text;  // 状态文案
        private String create_time;  // 创建时间
        private String goods_name;   // 商品名称
    }
}
