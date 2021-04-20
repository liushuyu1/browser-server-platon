package com.platon.browser.bean;

import lombok.Data;

/**
 * 区块&交易持久化事件
 */
@Data
public class PersistenceEvent extends ComplementEvent {

    private String traceId;

}
