package com.aiops.alert.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：启用分页插件（PaginationInnerInterceptor），
 * 让 mapper.selectPage 在 H2 / MySQL 双 profile 下都正确返回 total。
 *
 * 不加这个插件时 Page#getTotal() 永远是 0，且 LIMIT 不会被注入到 SQL，
 * 导致 page=1&size=20 返回全表数据（已被 AI 统计页测试发现）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // dev profile 用 H2，prod profile 用 MySQL；
        // PaginationInnerInterceptor 默认会按运行时方言判断，但显式指定可以兜底
        // 我们的 H2 跑在 MODE=MySQL 下，所以这里也按 MYSQL 写
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
