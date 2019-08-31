package com.platon.browser.engine.bean;

import lombok.Data;

import java.util.*;

/**
 * @Auther: Chendongming
 * @Date: 2019/8/21 15:09
 * @Description: 年化率信息bean
 */
@Data
public class AnnualizedRateInfo {
    private List<PeriodValueElement> profit = new ArrayList<>();
    private List<PeriodValueElement> cost = new ArrayList<>();
}
