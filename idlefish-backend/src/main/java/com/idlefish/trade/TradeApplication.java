package com.idlefish.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 闲置集 C2C 二手交易小程序后端 —— V1.0 单体应用入口。
 * 按领域分包：common / user / item / trade / search / im / risk / admin。
 */
@SpringBootApplication
@MapperScan("com.idlefish.trade")
@EnableScheduling
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}
