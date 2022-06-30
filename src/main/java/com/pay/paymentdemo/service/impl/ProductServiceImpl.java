package com.pay.paymentdemo.service.impl;

import com.pay.paymentdemo.entity.Product;
import com.pay.paymentdemo.mapper.ProductMapper;
import com.pay.paymentdemo.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
