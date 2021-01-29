package com.platon.browser.bean.ppos;

import lombok.Data;

@Data
public class ActiveAnalyzer {
    private String delegateCreateAnalyzer="delegateCreateAnalyzer";
    private String delegateExitAnalyzer="delegateExitAnalyzer";
    private String delegateRewardClaimAnalyzer="delegateRewardClaimAnalyzer";
    private String proposalCancelAnalyzer="proposalCancelAnalyzer";
    private String proposalParameterAnalyzer="proposalParameterAnalyzer";
    private String proposalTextAnalyzer="proposalTextAnalyzer";
    private String proposalUpgradeAnalyzer="proposalUpgradeAnalyzer";
    private String proposalVoteAnalyzer="proposalVoteAnalyzer";
    private String reportAnalyzer="reportAnalyzer";
    private String restrictingCreateAnalyzer="restrictingCreateAnalyzer";
    private String stakeCreateAnalyzer="stakeCreateAnalyzer";
    private String stakeExitAnalyzer="stakeExitAnalyzer";
    private String stakeIncreaseAnalyzer="stakeIncreaseAnalyzer";
    private String stakeModifyAnalyzer="stakeModifyAnalyzer";
    private String versionDeclareAnalyzer="versionDeclareAnalyzer";
}
