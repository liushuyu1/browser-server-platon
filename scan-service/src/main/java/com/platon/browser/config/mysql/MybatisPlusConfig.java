package com.platon.browser.config.mysql;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.platon.browser.interceptor.SqlInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class MybatisPlusConfig {


    /**
     * 分页插件
     *
     * @param
     * @return com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/8
     */
    @Bean
    @Order(1)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.MYSQL);
        paginationInnerInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    /**
     * SQL拦截器
     *
     * @param
     * @return com.platon.browser.interceptor.MyBatisPlusInterceptor
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    @Bean
    @Order(2)
    public SqlInterceptor myBatisInterceptor() {
        return new SqlInterceptor();
    }

}
