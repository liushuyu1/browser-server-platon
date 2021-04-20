package com.platon.browser.handler;

import com.platon.browser.bean.CommonConstant;
import com.platon.browser.utils.CommonUtil;
import com.platon.protocol.core.methods.response.PlatonBlock;
import com.lmax.disruptor.EventHandler;
import com.platon.browser.analyzer.BlockAnalyzer;
import com.platon.browser.bean.BlockEvent;
import com.platon.browser.bean.CollectionBlock;
import com.platon.browser.bean.ReceiptResult;
import com.platon.browser.exception.BeanCreateOrUpdateException;
import com.platon.browser.exception.BlankResponseException;
import com.platon.browser.exception.ContractInvokeException;
import com.platon.browser.publisher.CollectionEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * 区块事件处理器
 */
@Slf4j
@Component
public class BlockEventHandler implements EventHandler<BlockEvent> {

    @Resource
    private CollectionEventPublisher collectionEventPublisher;

    @Resource
    private BlockAnalyzer blockAnalyzer;

    @Override
    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE, label = "BlockEventHandler")
    public void onEvent(BlockEvent event, long sequence, boolean endOfBatch)
            throws ExecutionException, InterruptedException, BeanCreateOrUpdateException, IOException,
            ContractInvokeException, BlankResponseException {
        MDC.put(CommonConstant.TRACE_ID, event.getTraceId());
        long startTime = System.currentTimeMillis();
        try {
            PlatonBlock.Block rawBlock = event.getBlockCF().get().getBlock();
            ReceiptResult receiptResult = event.getReceiptCF().get();
            log.info("当前区块[{}]有[{}]笔交易", rawBlock.getNumber(), CommonUtil.ofNullable(() -> rawBlock.getTransactions().size()).orElse(0));
            // 分析区块 & 区块内的交易
            CollectionBlock block = blockAnalyzer.analyze(rawBlock, receiptResult);
            block.setReward(event.getEpochMessage().getBlockReward().toString());
            collectionEventPublisher.publish(block, block.getTransactions(), event.getEpochMessage(), event.getTraceId());
            // 释放对象引用
            event.releaseRef();
        } catch (Exception e) {
            log.error("区块事件处理异常", e);
            throw e;
        }
        log.info("处理耗时:{} ms", System.currentTimeMillis() - startTime);
        MDC.remove(CommonConstant.TRACE_ID);
    }

}