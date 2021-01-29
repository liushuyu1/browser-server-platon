package com.platon.browser.analyzer;

import com.platon.browser.analyzer.ppos.PPOSAnalyzer;
import com.platon.browser.bean.ppos.AnalyzerVersion;
import com.platon.browser.config.PPOSAnalyzerConfig;
import com.platon.browser.utils.SpringContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigInteger;

@Data
@Slf4j
@Component
public class PPOSAnalyzerProxy extends SpringContext {
    @Resource
    private PPOSAnalyzerConfig analyzerConfig;
    private AnalyzerVersion activeAnalyzerVersion=new AnalyzerVersion();
    // 激活指定版本的PPOS分析器，此方法在OnNewBlockAnalyzer方法中檢測到提案生效時調用
    public void activeVersion(String pipId) {
        analyzerConfig.getAnalyzerVersion().forEach(analyzerVersion->{
            if(
                analyzerConfig.getChainCurrentVersion().compareTo(new BigInteger(analyzerVersion.getActiveVersion()))==0
                &&analyzerVersion.getProposalId().equals(pipId)
            ){
                // 鏈當前版本及提案ID與PPOS分析器匹配，則激活相應PPOS分析器
                activeAnalyzerVersion = analyzerVersion;
            }
        });
    }

    public PPOSAnalyzer getDelegateCreateAnalyzer(){
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getDelegateCreateAnalyzer());
    }

    public PPOSAnalyzer getDelegateExitAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getDelegateExitAnalyzer());
    }

    public PPOSAnalyzer getDelegateRewardClaimAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getDelegateRewardClaimAnalyzer());
    }

    public PPOSAnalyzer getProposalCancelAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getProposalCancelAnalyzer());
    }

    public PPOSAnalyzer getProposalParameterAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getProposalParameterAnalyzer());
    }

    public PPOSAnalyzer getProposalTextAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getProposalTextAnalyzer());
    }

    public PPOSAnalyzer getProposalUpgradeAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getProposalUpgradeAnalyzer());
    }

    public PPOSAnalyzer getProposalVoteAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getProposalVoteAnalyzer());
    }

    public PPOSAnalyzer getReportAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getReportAnalyzer());
    }

    public PPOSAnalyzer getRestrictingCreateAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getRestrictingCreateAnalyzer());
    }

    public PPOSAnalyzer getStakeCreateAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getStakeCreateAnalyzer());
    }

    public PPOSAnalyzer getStakeExitAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getStakeExitAnalyzer());
    }

    public PPOSAnalyzer getStakeIncreaseAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getStakeIncreaseAnalyzer());
    }

    public PPOSAnalyzer getStakeModifyAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getStakeModifyAnalyzer());
    }

    public PPOSAnalyzer getVersionDeclareAnalyzer() {
        return (PPOSAnalyzer)getBean(activeAnalyzerVersion.getActiveAnalyzer().getVersionDeclareAnalyzer());
    }
}
