package com.pay.paymentdemo.service;

import com.pay.paymentdemo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pay.paymentdemo.enums.OrderStatus;

import java.util.List;

public interface OrderInfoService extends IService<OrderInfo> {
    OrderInfo createOrderByProductId(Long productId, String paymentType);

    void saveCodeUrl(String orderNo, String codeUrl);

    List<OrderInfo> listOrderByCreateTimeDesc();

    void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus);

    String getOrderStatus(String orderNo);

    List<OrderInfo> getNoPayOrderByDuration(int minutes,String paymentType);

    OrderInfo getOrderByOrderNo(String orderNo);
}
