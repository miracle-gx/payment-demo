package com.pay.paymentdemo.controller;

import com.pay.paymentdemo.entity.Product;
import com.pay.paymentdemo.service.ProductService;
import com.pay.paymentdemo.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@CrossOrigin   // 开放前端的跨域访问
@Api(tags = "商品管理")
@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Resource
    private ProductService productService;

    @ApiOperation("测试接口")
    @GetMapping("/test")
    public R test() {
        return R.ok()
                .data("meaasge", "hello")   // 可以链式操作
                .data("now", new Date());
    }

    // 获取商品列表
    @GetMapping("/list")
    public R list() {
        List<Product> list = productService.list();
        return R.ok().data("productList", list);
    }
}
