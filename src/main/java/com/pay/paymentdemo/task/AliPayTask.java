package com.pay.paymentdemo.task;

import com.pay.paymentdemo.entity.OrderInfo;
import com.pay.paymentdemo.enums.PayType;
import com.pay.paymentdemo.service.AliPayService;
import com.pay.paymentdemo.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
@Slf4j
public class AliPayTask {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private AliPayService aliPayService;

    /**
     * 从第0秒开始每个30秒执行1次，查询创建超过5分钟，并且未支付的订单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void orderComfirm() {
        log.info("orderComfirm 被执行中....");

        List<OrderInfo> orderInfoList = orderInfoService.getNoPayOrderByDuration(1, PayType.ALIPAY.getType());

        for (OrderInfo orderInfo : orderInfoList) {
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单   ===> {}", orderNo);

            // 核实订单状态: 调用支付宝查单接口
            aliPayService.checkOrderStatus(orderNo);

        }
    }
}
