package com.platon.browser.resp.staking;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.platon.browser.config.CustomLatSerializer;

public class StakingStatisticNewResp {

	private String stakingDelegationValue; // 质押委托总数
	private String stakingValue; // 质押总数
	private String issueValue; // 发行量
	private String blockReward; // 当前的出块奖励
	private String stakingReward; // 当前的质押奖励
	private Long currentNumber; // 当前区块高度
	private Long addIssueBegin; // 当前增发周期的开始快高
	private Long addIssueEnd; // 当前增发周期的结束块高
	private Long nextSetting; // 离下个结算周期倒计时
	@JsonSerialize(using = CustomLatSerializer.class)
	public String getStakingDelegationValue() {
		return stakingDelegationValue;
	}
	public void setStakingDelegationValue(String stakingDelegationValue) {
		this.stakingDelegationValue = stakingDelegationValue;
	}
	public String getStakingValue() {
		return stakingValue;
	}
	public void setStakingValue(String stakingValue) {
		this.stakingValue = stakingValue;
	}
	public String getIssueValue() {
		return issueValue;
	}
	public void setIssueValue(String issueValue) {
		this.issueValue = issueValue;
	}
	@JsonSerialize(using = CustomLatSerializer.class)
	public String getBlockReward() {
		return blockReward;
	}
	public void setBlockReward(String blockReward) {
		this.blockReward = blockReward;
	}
	@JsonSerialize(using = CustomLatSerializer.class)
	public String getStakingReward() {
		return stakingReward;
	}
	public void setStakingReward(String stakingReward) {
		this.stakingReward = stakingReward;
	}
	public Long getCurrentNumber() {
		return currentNumber;
	}
	public void setCurrentNumber(Long currentNumber) {
		this.currentNumber = currentNumber;
	}
	public Long getAddIssueBegin() {
		return addIssueBegin;
	}
	public void setAddIssueBegin(Long addIssueBegin) {
		this.addIssueBegin = addIssueBegin;
	}
	public Long getAddIssueEnd() {
		return addIssueEnd;
	}
	public void setAddIssueEnd(Long addIssueEnd) {
		this.addIssueEnd = addIssueEnd;
	}
	public Long getNextSetting() {
		return nextSetting;
	}
	public void setNextSetting(Long nextSetting) {
		this.nextSetting = nextSetting;
	}
	
}
