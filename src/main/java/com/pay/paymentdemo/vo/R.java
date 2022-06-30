package com.pay.paymentdemo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

// 统一结果
@Data
@Accessors(chain = true)   // 支持链式操作
public class R {
    private Integer code;  // 响应码
    private String message; // 响应消息
    private Map<String, Object> data = new HashMap<>();

    public static R ok() {
        R r = new R();
        r.setCode(0);
        r.setMessage("成功");
        return r;
    }

    public static R error() {
        R r = new R();
        r.setCode(-1);
        r.setMessage("失败");
        return r;
    }

    // 返回带有后端给前端数据的信息
    public R data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
