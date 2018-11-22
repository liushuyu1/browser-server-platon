package com.platon.browser.req.transaction;

import com.platon.browser.common.req.PageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = false)
public class PendingTxListReq extends PageReq {
    @NotBlank(message = "{chain.id.notnull}")
    private String cid;
    private String address;
}
