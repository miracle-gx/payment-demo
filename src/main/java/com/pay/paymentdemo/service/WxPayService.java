package com.pay.paymentdemo.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

public interface WxPayService {

    public Map<String,Object> nativePay(Long productId) throws Exception;

    void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException;

    void cancelOrder(String orderNo) throws Exception;

    String queryOrder(String orderNo) throws Exception;

    void checkOrderStatus(String orderNo) throws Exception;

    void refund(String orderNo, String reason) throws Exception;

    String queryRefund(String refundNo) throws Exception;

    void processRefund(Map<String, Object> bodyMap) throws Exception;

    String queryBill(String billDate, String type) throws Exception;

    String downloadBill(String billDate, String type) throws Exception;

    Map<String, Object> nativePayV2(Long productId, String remoteAddr) throws Exception;
}
