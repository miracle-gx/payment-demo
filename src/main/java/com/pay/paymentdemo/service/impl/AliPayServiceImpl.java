package com.pay.paymentdemo.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.pay.paymentdemo.entity.OrderInfo;
import com.pay.paymentdemo.entity.RefundInfo;
import com.pay.paymentdemo.enums.OrderStatus;
import com.pay.paymentdemo.enums.PayType;
import com.pay.paymentdemo.enums.alipay.AliPayTradeState;
import com.pay.paymentdemo.enums.wxpay.WxApiType;
import com.pay.paymentdemo.enums.wxpay.WxTradeState;
import com.pay.paymentdemo.service.AliPayService;
import com.pay.paymentdemo.service.OrderInfoService;
import com.pay.paymentdemo.service.PaymentInfoService;
import com.pay.paymentdemo.service.RefundInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class AliPayServiceImpl implements AliPayService {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private Environment config;

    @Resource
    private PaymentInfoService paymentInfoService;

    private ReentrantLock lock = new ReentrantLock();

    @Resource
    private RefundInfoService refundInfoService;

    @Transactional
    @Override
    public String tradeCreate(Long productId) {

        try {
            // 生成订单
            log.info("生成订单");
            OrderInfo orderInfo = orderInfoService.createOrderByProductId(productId, PayType.ALIPAY.getType());

            // 调用支付宝接口
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            // 配置需要的公共请求参数
            request.setNotifyUrl(config.getProperty("alipay.notify-url"));
            request.setReturnUrl(config.getProperty("alipay.return-url"));

            // 组装当前业务方法的请求参数
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderInfo.getOrderNo());
            BigDecimal total = new BigDecimal(orderInfo.getTotalFee().toString()).divide(new BigDecimal("100"));
           bizContent.put("total_amount", total);
            bizContent.put("subject", orderInfo.getTitle());
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

            request.setBizContent(bizContent.toString());

            // 执行请求，调用支付宝接口
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if(response.isSuccess()){
                log.info("调用成功，返回结果   ===> {}", response.getBody());
                return response.getBody();
            } else {
                log.info("调用失败，返回码  ===> {}, 返回描述  ===> {}", response.getCode(), response.getMsg());
                throw new RuntimeException("创建支付交易失败");
            }

        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("创建支付交易失败");
        }
    }

    /**
     * 处理订单
     * @param params
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processOrder(Map<String, String> params) {

        log.info("处理订单");
        // 获取订单号
        String orderNo = params.get("out_trade_no");

        // 在对业务数据进行状态检查和处理前，要才有数据锁进行并发控制
        // 以避免函数重入造成的数据混乱
        // 处理重复通知
        // 接口调用的幂等性：无论接口调用多少次，以下业务执行一次
        if(lock.tryLock()) {
            try {
                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.NOTPAY.getType().equals(orderStatus)) {
                    return;
                }

                // 更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);

                // 记录支付日志
                paymentInfoService.createPaymentInfoForAliPay(params);
            } finally {
                lock.unlock();
            }
        }

    }

    /**
     * 用户取消订单
     * @param orderNo
     */
    @Override
    public void cancelOrder(String orderNo) {

        // 调用支付宝提供的统一收单交易关闭接口
        this.closeOrder(orderNo);

        // 更新用户订单状态
        orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CANCEL);

    }

    /**
     * 查询订单
     * @param orderNo
     * @return  返回订单查询结果，若返回null则表示支付宝端尚未创建订单
     */
    @Override
    public String queryOrder(String orderNo) {
        try {
            log.info("查单接口调用   ====> {}", orderNo);

            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(bizContent.toString());
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功，返回结果   ===> {}", response.getBody());
                return response.getBody();
            } else {
                log.info("调用失败，返回码  ===> {}, 返回描述  ===> {}", response.getCode(), response.getMsg());
//                throw new RuntimeException("查单接口调用失败");
                return null;   // 订单不存在
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("查单接口调用失败");
        }

    }

    /**
     * 根据订单号调用支付宝查单接口，核实订单状态
     * 若订单未创建，则更新商户端订单状态
     * 如果订单已支付，则更新商户端订单状态，并记录支付日志
     * 若订单未支付，则调用关单接口关闭订单，并更新商户端订单状态
     * @param orderNo
     */
    @Override
    public void checkOrderStatus(String orderNo) {
        log.warn("根据订单号核实订单状态  ===> {}", orderNo);

        // 调用支付宝查单接口
        String result = this.queryOrder(orderNo);
        if(result == null) {
            log.warn("核实订单未创建   ====> {}", orderNo);
            // 更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CLOSED);
        }

        // 解析查单响应结果
        Gson gson = new Gson();
        HashMap<String, LinkedTreeMap> resultMap = gson.fromJson(result, HashMap.class);
        LinkedTreeMap alipayTradeQueryResponse = resultMap.get("alipay_trade_query_response");

        // 获取支付宝支付端的订单状态
        String tradeStatus = (String) alipayTradeQueryResponse.get("trade_status");

        // 判断订单状态
        if(AliPayTradeState.NOTPAY.getType().equals(tradeStatus)) {
            log.warn("核实订单未支付  ===> {}", orderNo);

            // 若确认订单未支付，则调用关单接口
            this.closeOrder(orderNo);
            // 记录本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CLOSED);
        }

        if(AliPayTradeState.SUCCESS.getType().equals(tradeStatus)) {
            log.warn("核实订单已支付  ===> {}", orderNo);
            // 若确认订单已支付，则更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);
            // 记录支付日志
            paymentInfoService.createPaymentInfoForAliPay(alipayTradeQueryResponse);

        }
    }

    @Transactional(rollbackFor =  Exception.class)
    @Override
    public void refund(String orderNo, String reason) {
        try {
            log.info("调用退款API");

            // 创建退款单
            RefundInfo refundInfo = refundInfoService.createRefundByOrderNoForAliPay(orderNo, reason);

            // 调用统一收单交易退款接口
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

            // 组装当前业务方法的请求参数
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderNo);
            BigDecimal refund = new BigDecimal(refundInfo.getRefund().toString()).divide(new BigDecimal("100"));
            // 退款失败模拟
            // BigDecimal refund = new BigDecimal(refundInfo.getRefund().toString()).divide(new BigDecimal("50"));

            bizContent.put("refund_amount", refund);
            bizContent.put("refund_reason", reason);
            request.setBizContent(bizContent.toString());

            // 执行请求，调用支付宝接口
            AlipayTradeRefundResponse response = alipayClient.execute(request);

            if(response.isSuccess()){
                log.info("调用成功，返回结果 ===> " + response.getBody());

                // 更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

                // 更新退款单
                refundInfoService.updateRefundForAliPay(
                        refundInfo.getRefundNo(),
                        response.getBody(),
                        AliPayTradeState.REFUND_SUCCESS.getType()
                );  // 退款成功
            } else {

                log.info("调用失败，返回码 ===> " + response.getCode() + ", 返回描述 ===> " + response.getMsg());

                // 更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL);

                // 更新退款单
                refundInfoService.updateRefundForAliPay(
                        refundInfo.getRefundNo(),
                        response.getBody(),
                        AliPayTradeState.REFUND_ERROR.getType()
                );   // 退款失败
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关单接口的调用
     * @param orderNo
     */
    private void closeOrder(String orderNo) {

        try {
            log.info("关单接口的调用  ===> {}", orderNo);

            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(bizContent.toString());
            AlipayTradeCloseResponse response = alipayClient.execute(request);

            if(response.isSuccess()){
                log.info("调用成功，返回结果   ===> {}", response.getBody());
            } else {
                log.info("调用失败，返回码  ===> {}, 返回描述  ===> {}", response.getCode(), response.getMsg());
//                throw new RuntimeException("关单接口调用失败");
            }

        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("关单接口调用失败");
        }
    }

    /**
     * 查询退款
     * @param orderNo
     * @return
     */
    @Override
    public String queryRefund(String orderNo) {

        try {
            log.info("查询退款接口调用 ===> {}", orderNo);

            AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderNo);
            bizContent.put("out_request_no", orderNo);
            request.setBizContent(bizContent.toString());

            AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功，返回结果 ===> " + response.getBody());
                return response.getBody();
            } else {
                log.info("调用失败，返回码 ===> " + response.getCode() + ", 返回描述 ===> " + response.getMsg());
                //throw new RuntimeException("查单接口的调用失败");
                return null;//订单不存在
            }

        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("查单接口的调用失败");
        }
    }

    /**
     * 申请账单
     * @param billDate
     * @param type
     * @return
     */
    @Override
    public String queryBill(String billDate, String type) {

        try {

            AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("bill_type", type);
            bizContent.put("bill_date", billDate);
            request.setBizContent(bizContent.toString());
            AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient.execute(request);

            if(response.isSuccess()){
                log.info("调用成功，返回结果 ===> " + response.getBody());

                //获取账单下载地址
                Gson gson = new Gson();
                HashMap<String, LinkedTreeMap> resultMap = gson.fromJson(response.getBody(), HashMap.class);
                LinkedTreeMap billDownloadurlResponse = resultMap.get("alipay_data_dataservice_bill_downloadurl_query_response");
                String billDownloadUrl = (String)billDownloadurlResponse.get("bill_download_url");

                return billDownloadUrl;
            } else {
                log.info("调用失败，返回码 ===> " + response.getCode() + ", 返回描述 ===> " + response.getMsg());
                throw new RuntimeException("申请账单失败");
            }

        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("申请账单失败");
        }
    }
}
