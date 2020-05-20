package com.platon.browser.service.govern;

import com.platon.browser.client.PlatOnClient;
import com.platon.browser.config.BlockChainConfig;
import com.platon.browser.config.govern.ModifiableParam;
import com.platon.browser.dao.entity.Config;
import com.platon.browser.dao.mapper.ConfigMapper;
import com.platon.browser.dao.mapper.CustomConfigMapper;
import com.platon.browser.enums.ModifiableGovernParamEnum;
import com.platon.sdk.contracts.ppos.dto.resp.GovernParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @description: 治理参数服务
 * @author: chendongming@juzix.net
 * @create: 2019-11-25 20:36:04
 **/
@Service
@Transactional
public class ParameterService {

    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private PlatOnClient platOnClient;
    @Autowired
    private BlockChainConfig chainConfig;
    @Autowired
    private CustomConfigMapper customConfigMapper;

    /**
     * 使用debug_economic_config接口返回的数据初始化配置表，只有从第一个块开始同步时需要调用
     */
    public void initConfigTable() throws Exception {
        configMapper.deleteByExample(null);
        List<GovernParam> governParamList = platOnClient.getProposalContract().getParamList("").send().getData();
        List<Config> configList = new ArrayList<>();
        int id = 1;
        Date date = new Date();
        for (GovernParam gp : governParamList) {
            Config config = new Config();
            config.setId(id);
            config.setModule(gp.getParamItem().getModule());
            config.setName(gp.getParamItem().getName());
            config.setRangeDesc(gp.getParamItem().getDesc());
            config.setActiveBlock(0L);
            configList.add(config);
            config.setCreateTime(date);
            config.setUpdateTime(date);

            // 更新内存中的blockChainConfig中在init_value,stale_value,value字段值
            String initValue = getValueInBlockChainConfig(config.getName());
            config.setInitValue(initValue);
            config.setStaleValue(initValue);
            config.setValue(initValue);
            id++;
        }
        configMapper.batchInsert(configList);
    }

    /**
     * 使用配置表中的配置覆盖内存中的BlockChainConfig，在重新启动的时候调用
     */
    public void overrideBlockChainConfig(){
        // 使用数据库config表的配置覆盖当前配置
        List<Config> configList = configMapper.selectByExample(null);
        ModifiableParam modifiableParam = ModifiableParam.builder().build().init(configList);

        //创建验证人最低的质押Token数(K)
        chainConfig.setStakeThreshold(modifiableParam.getStaking().getStakeThreshold());
        //委托人每次委托及赎回的最低Token数(H)
        chainConfig.setDelegateThreshold(modifiableParam.getStaking().getOperatingThreshold());
        //节点质押退回锁定周期
        chainConfig.setUnStakeRefundSettlePeriodCount(modifiableParam.getStaking().getUnStakeFreezeDuration().toBigInteger());
        //备选结算周期验证节点数量(U)
        chainConfig.setSettlementValidatorCount(modifiableParam.getStaking().getMaxValidators().toBigInteger());
        //举报最高处罚n3‱
        chainConfig.setDuplicateSignSlashRate(modifiableParam.getSlashing().getSlashFractionDuplicateSign().divide(BigDecimal.valueOf(10000),16, RoundingMode.FLOOR));
        //举报奖励n4%
        chainConfig.setDuplicateSignRewardRate(modifiableParam.getSlashing().getDuplicateSignReportReward().divide(BigDecimal.valueOf(100),2,RoundingMode.FLOOR));
        //证据有效期
        chainConfig.setEvidenceValidEpoch(modifiableParam.getSlashing().getMaxEvidenceAge());
        //扣除区块奖励的个数
        chainConfig.setSlashBlockRewardCount(modifiableParam.getSlashing().getSlashBlocksReward());
        //默认每个区块的最大Gas
        chainConfig.setMaxBlockGasLimit(modifiableParam.getBlock().getMaxBlockGasLimit());
        // 零出块次数阈值，在指定时间范围内达到该次数则处罚
        chainConfig.setZeroProduceNumberThreshold(modifiableParam.getSlashing().getZeroProduceNumberThreshold());
        // 上一次零出块后，在往后的N个共识周期内如若再出现零出块，则在这N个共识周期完成时记录零出块信息
        chainConfig.setZeroProduceCumulativeTime(modifiableParam.getSlashing().getZeroProduceCumulativeTime());
        chainConfig.setRewardPerChangeInterval(modifiableParam.getStaking().getRewardPerChangeInterval());
        chainConfig.setRewardPerMaxChangeRange(modifiableParam.getStaking().getRewardPerMaxChangeRange());
        chainConfig.setAddIssueRate(modifiableParam.getReward().getIncreaseIssuanceRatio().divide(new BigDecimal(10000)));
    }

    /**
     * 配置值轮换：value旧值覆盖到stale_value，参数中的新值覆盖value
     * @param activeConfigList 被激活的配置信息列表
     */
    @Transactional
    public void rotateConfig(List<Config> activeConfigList) {
        // 更新配置表
        customConfigMapper.rotateConfig(activeConfigList);
        //更新内存中的BlockChainConfig
        overrideBlockChainConfig();
    }

    /**
     * 根据参数提案中的参数name获取当前blockChainConfig中的对应的当前值
     * @param name
     * @return
     */
    public String getValueInBlockChainConfig(String name) {
        ModifiableGovernParamEnum paramEnum = ModifiableGovernParamEnum.getMap().get(name);
        String staleValue = "";
        switch (paramEnum){
            // 质押相关
            case STAKE_THRESHOLD: 
                staleValue = chainConfig.getStakeThreshold().toString();
                break;
            case OPERATING_THRESHOLD:
                staleValue = chainConfig.getDelegateThreshold().toString();
                break;
            case MAX_VALIDATORS:
                staleValue = chainConfig.getSettlementValidatorCount().toString();
                break;
            case UN_STAKE_FREEZE_DURATION:
                staleValue = chainConfig.getUnStakeRefundSettlePeriodCount().toString();
                break;
            // 惩罚相关
            case SLASH_FRACTION_DUPLICATE_SIGN:
                staleValue = chainConfig.getDuplicateSignSlashRate().multiply(BigDecimal.valueOf(10000)).toString();
                break;
            case DUPLICATE_SIGN_REPORT_REWARD:
                staleValue = chainConfig.getDuplicateSignRewardRate().multiply(BigDecimal.valueOf(100)).toString();
                break;
            case MAX_EVIDENCE_AGE:
                staleValue = chainConfig.getEvidenceValidEpoch().toString();
                break;
            case SLASH_BLOCKS_REWARD:
                staleValue = chainConfig.getSlashBlockRewardCount().toString();
                break;
            // 区块相关
            case MAX_BLOCK_GAS_LIMIT:
                staleValue = chainConfig.getMaxBlockGasLimit().toString();
                break;
            // 零出块次数阈值，在指定时间范围内达到该次数则处罚
            case ZERO_PRODUCE_NUMBER_THRESHOLD:
                staleValue = chainConfig.getZeroProduceNumberThreshold().toString();
                break;
            // 上一次零出块后，在往后的N个共识周期内如若再出现零出块，则在这N个共识周期完成时记录零出块信息
            case ZERO_PRODUCE_CUMULATIVE_TIME:
                staleValue = chainConfig.getZeroProduceCumulativeTime().toString();
                break;
            case REWARD_PER_MAX_CHANGE_RANGE:
                staleValue = chainConfig.getRewardPerMaxChangeRange().toString();
                break;
            case REWARD_PER_CHANGE_INTERVAL:
                staleValue = chainConfig.getRewardPerChangeInterval().toString();
                break;
            case INCREASE_ISSUANCE_RATIO:
                staleValue = chainConfig.getAddIssueRate().toString();
                break;
            default:
                break;
        }
        return staleValue;
    }
}
