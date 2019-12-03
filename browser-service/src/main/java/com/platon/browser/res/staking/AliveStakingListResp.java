package com.platon.browser.res.staking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.platon.browser.config.CustomLowLatSerializer;

/**
 * 活跃验证人列表返回对象
 *  @file AliveStakingListResp.javaStakingListResp
 *  @description 
 *	@author zhangrj
 *  @data 2019年8月31日
 */
public class AliveStakingListResp {

	private Integer ranking;           //排行
	private String nodeId;            //节点id
	private String nodeName;          //验证人名称
	private String stakingIcon;       //验证人图标
	private Integer status;            //状态   1:候选中  2:活跃中  3:出块中
	private String totalValue;        //质押总数=有效的质押+委托
	private String delegateValue;     //委托总数
	private Integer delegateQty;       //委托人数
	private Integer slashLowQty;      //低出块率举报次数
	private Integer slashMultiQty;    //多签举报次数
	private Long blockQty;          //产生的区块数
	private String expectedIncome;    //预计年收化率（从验证人加入时刻开始计算）
	private Boolean isRecommend;     //是否官方推荐
	private Boolean isInit;          //是否为初始节点 
	public Integer getRanking() {
		return ranking;
	}
	public void setRanking(Integer ranking) {
		this.ranking = ranking;
	}
	public String getNodeId() {
		return nodeId;
	}
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	public String getNodeName() {
		return nodeName;
	}
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	public String getStakingIcon() {
		return stakingIcon;
	}
	public void setStakingIcon(String stakingIcon) {
		this.stakingIcon = stakingIcon;
	}
	public Integer getStatus() {
		return status;
	}
	public void setStatus(Integer status) {
		this.status = status;
	}
	@JsonSerialize(using = CustomLowLatSerializer.class)
	public String getTotalValue() {
		return totalValue;
	}
	public void setTotalValue(String totalValue) {
		this.totalValue = totalValue;
	}
	@JsonSerialize(using = CustomLowLatSerializer.class)
	public String getDelegateValue() {
		return delegateValue;
	}
	public void setDelegateValue(String delegateValue) {
		this.delegateValue = delegateValue;
	}
	public Integer getDelegateQty() {
		return delegateQty;
	}
	public void setDelegateQty(Integer delegateQty) {
		this.delegateQty = delegateQty;
	}
	public Integer getSlashLowQty() {
		return slashLowQty;
	}
	public void setSlashLowQty(Integer slashLowQty) {
		this.slashLowQty = slashLowQty;
	}
	public Integer getSlashMultiQty() {
		return slashMultiQty;
	}
	public void setSlashMultiQty(Integer slashMultiQty) {
		this.slashMultiQty = slashMultiQty;
	}
	public Long getBlockQty() {
		return blockQty;
	}
	public void setBlockQty(Long blockQty) {
		this.blockQty = blockQty;
	}
	public String getExpectedIncome() {
		return expectedIncome;
	}
	public void setExpectedIncome(String expectedIncome) {
		this.expectedIncome = expectedIncome;
	}
	public Boolean getIsRecommend() {
		return isRecommend;
	}
	public void setIsRecommend(Boolean isRecommend) {
		this.isRecommend = isRecommend;
	}
	public Boolean getIsInit() {
		return isInit;
	}
	public void setIsInit(Boolean isInit) {
		this.isInit = isInit;
	}
	
	
}
