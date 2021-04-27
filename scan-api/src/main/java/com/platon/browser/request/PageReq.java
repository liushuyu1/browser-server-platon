package com.platon.browser.request;

import javax.validation.constraints.NotNull;


/**
 * 分页对象
 *
 * @author zhangrj
 * @file PageReq.java
 * @description
 * @data 2019年8月31日
 */
public class PageReq {

    /**
     * 当前页
     */
    @NotNull(message = "{pageNo not null}")
    private Integer pageNo = 1;

    /**
     * 页大小
     */
    @NotNull(message = "{pageSize not null}")
    private Integer pageSize = 10;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        if (pageSize > 1000) {
            this.pageSize = 1000;
        } else {
            this.pageSize = pageSize;
        }
    }

}
