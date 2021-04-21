package com.platon.browser.handler;

import com.lmax.disruptor.EventHandler;
import com.platon.browser.bean.CommonConstant;
import com.platon.browser.bean.ComplementEvent;
import com.platon.browser.publisher.PersistenceEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 区块事件处理器
 */
@Slf4j
@Component
public class ComplementEventHandler implements EventHandler<ComplementEvent> {

    @Resource
    private PersistenceEventPublisher persistenceEventPublisher;

    @Override
    public void onEvent(ComplementEvent event, long sequence, boolean endOfBatch) {
        surroundExec(event, sequence, endOfBatch);
    }

    private void surroundExec(ComplementEvent event, long sequence, boolean endOfBatch) {
        MDC.put(CommonConstant.TRACE_ID, event.getTraceId());
        long startTime = System.currentTimeMillis();
        exec(event, sequence, endOfBatch);
        log.info("处理耗时:{} ms", System.currentTimeMillis() - startTime);
        MDC.remove(CommonConstant.TRACE_ID);
    }

    private void exec(ComplementEvent event, long sequence, boolean endOfBatch) {
        try {
            // 发布至持久化队列
            persistenceEventPublisher.publish(event.getBlock(), event.getTransactions(), event.getNodeOpts(), event.getDelegationRewards(), event.getTraceId());
            // 释放对象引用
            event.releaseRef();
        } catch (Exception e) {
            log.error("", e);
            throw e;
        }
    }

}