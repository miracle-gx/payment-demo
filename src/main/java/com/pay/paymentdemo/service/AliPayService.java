package com.pay.paymentdemo.service;

import com.alipay.api.AlipayApiException;

import java.util.Map;

public interface AliPayService {
    String tradeCreate(Long productId);

    void processOrder(Map<String, String> params);

    void cancelOrder(String orderNo);

    String queryOrder(String orderNo);

    void checkOrderStatus(String orderNo);
}
