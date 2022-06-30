package com.pay.paymentdemo.controller;

import com.pay.paymentdemo.entity.OrderInfo;
import com.pay.paymentdemo.enums.OrderStatus;
import com.pay.paymentdemo.service.OrderInfoService;
import com.pay.paymentdemo.vo.R;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@CrossOrigin   // 开放前端的跨域访问
@Api(tags = "商品订单管理")
@RestController
@RequestMapping("/api/order-info")
@Slf4j
public class OrderInfoController {
    @Resource
    private OrderInfoService orderInfoService;

    @GetMapping("/list")
    public R list() {
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        return R.ok().data("list", list);
    }

    /*
     * 查询本地订单状态
     * */
    @GetMapping("query-order-status/{orderNo}")
    public R queryOrderStatus(@PathVariable String orderNo) {
        log.info("查询订单状态");
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if(OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功"); // 支付成功
        }
        return R.ok().setCode(101).setMessage("支付中...");
    }
}
