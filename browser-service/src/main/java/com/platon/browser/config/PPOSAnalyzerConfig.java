package com.platon.browser.config;

import com.platon.browser.bean.ppos.AnalyzerVersion;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix="ppos")
public class PPOSAnalyzerConfig {
	private BigInteger chainCurrentVersion;
	private List<AnalyzerVersion> analyzerVersion;
}
