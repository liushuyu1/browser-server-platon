package com.platon.browser.bean.ppos;

import lombok.Data;

@Data
public class AnalyzerVersion {
    private String proposalId;
    private String activeVersion;
    private ActiveAnalyzer activeAnalyzer=new ActiveAnalyzer();
}
