package com.pay.paymentdemo.service;

import com.pay.paymentdemo.entity.RefundInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundByOrderNo(String orderNo, String reason);

    void updateRefund(String bodyAsString);

    RefundInfo createRefundByOrderNoForAliPay(String orderNo, String reason);

    void updateRefundForAliPay(String refundNo, String content, String refundStatus);
}
