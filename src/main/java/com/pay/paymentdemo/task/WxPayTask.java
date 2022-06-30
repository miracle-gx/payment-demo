package com.pay.paymentdemo.task;

import com.pay.paymentdemo.entity.OrderInfo;
import com.pay.paymentdemo.enums.PayType;
import com.pay.paymentdemo.service.OrderInfoService;
import com.pay.paymentdemo.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
@Slf4j
public class WxPayTask {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private WxPayService wxPayService;

    /**
     * 秒 分 时 日 月 周
     * * : 以秒为例， 每秒都执行
     * 1-3 : 从第一秒开始执行，到第三秒结束执行
     * 0/3 : 从第0秒开始，每个3秒执行一次
     * 1,2,3 : 在指定的第1、2、3秒开始执行
     * ? : 不指定
     * 日和周不能同时指定，指定其中一个， 则另一个设置为?
     */
    /*@Scheduled(cron = "0/3 * * * * ?")   // 每月每日每时每分每3秒都执行
    public void task1() {
        log.info("task1 被执行中....");
    }*/

    /**
     * 从第0秒开始每个30秒执行1次，查询创建超过5分钟，并且未支付的订单
     */
    //@Scheduled(cron = "0/30 * * * * ?")
    public void orderComfirm() throws Exception {
        log.info("orderComfirm 被执行中....");

        List<OrderInfo> orderInfoList = orderInfoService.getNoPayOrderByDuration(1, PayType.WXPAY.getType());

        for (OrderInfo orderInfo : orderInfoList) {
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单   ===> {}", orderNo);

            // 核实订单状态: 调用微信支付查单接口
            wxPayService.checkOrderStatus(orderNo);
        }
    }

}
