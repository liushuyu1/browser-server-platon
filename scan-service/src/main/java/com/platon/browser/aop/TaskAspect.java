package com.platon.browser.aop;

import com.platon.browser.utils.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Aspect;

/**
 * 定时任务切面---添加链路id
 *
 * @author huangyongpeng@matrixelements.com
 * @date 2021/4/27
 */
@Slf4j
@Component
@Aspect
public class TaskAspect {

    @Pointcut(value = "@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public void access() {

    }

    @Before("access()")
    public void doBefore(JoinPoint joinPoint) {
        CommonUtil.putTraceId();
    }

    @After("access()")
    public void after(JoinPoint joinPoint) {
        CommonUtil.removeTraceId();
    }

}
