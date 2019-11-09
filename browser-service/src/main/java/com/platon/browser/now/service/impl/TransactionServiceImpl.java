package com.platon.browser.now.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.platon.browser.config.BlockChainConfig;
import com.platon.browser.dao.entity.*;
import com.platon.browser.dao.mapper.CustomVoteMapper;
import com.platon.browser.dao.mapper.ProposalMapper;
import com.platon.browser.dao.mapper.SlashMapper;
import com.platon.browser.dao.mapper.StakingMapper;
import com.platon.browser.dto.CustomStaking;
import com.platon.browser.dto.CustomVoteProposal;
import com.platon.browser.dto.account.AccountDownload;
import com.platon.browser.dto.keybase.KeyBaseUser;
import com.platon.browser.dto.transaction.TransactionCacheDto;
import com.platon.browser.elasticsearch.TransactionESRepository;
import com.platon.browser.elasticsearch.dto.Block;
import com.platon.browser.elasticsearch.dto.ESResult;
import com.platon.browser.elasticsearch.dto.Transaction;
import com.platon.browser.elasticsearch.service.impl.ESQueryBuilderConstructor;
import com.platon.browser.elasticsearch.service.impl.ESQueryBuilders;
import com.platon.browser.enums.I18nEnum;
import com.platon.browser.enums.RedeemStatusEnum;
import com.platon.browser.enums.ReqTransactionTypeEnum;
import com.platon.browser.now.service.TransactionService;
import com.platon.browser.now.service.cache.StatisticCacheService;
import com.platon.browser.param.*;
import com.platon.browser.req.PageReq;
import com.platon.browser.req.newtransaction.TransactionDetailsReq;
import com.platon.browser.req.newtransaction.TransactionListByAddressRequest;
import com.platon.browser.req.newtransaction.TransactionListByBlockRequest;
import com.platon.browser.res.RespPage;
import com.platon.browser.res.transaction.TransactionDetailsEvidencesResp;
import com.platon.browser.res.transaction.TransactionDetailsRPPlanResp;
import com.platon.browser.res.transaction.TransactionDetailsResp;
import com.platon.browser.res.transaction.TransactionListResp;
import com.platon.browser.util.*;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.utils.Convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * 交易方法逻辑实现
 *  @file TransactionServiceImpl.java
 *  @description
 *	@author zhangrj
 *  @data 2019年8月31日
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private final Logger logger = LoggerFactory.getLogger(TransactionServiceImpl.class);
    @Autowired
    private TransactionESRepository transactionESRepository;
    @Autowired
    private I18nUtil i18n;
    @Autowired
    private StakingMapper stakingMapper;
    @Autowired
    private SlashMapper slashMapper;
    @Autowired
    private ProposalMapper proposalMapper;
    @Autowired
    private CustomVoteMapper customVoteMapper;
    @Autowired
    private StatisticCacheService statisticCacheService;
    @Autowired
    private BlockChainConfig blockChainConfig;

    @Override
    public RespPage<TransactionListResp> getTransactionList( PageReq req) {
        RespPage<TransactionListResp> result = new RespPage<>();
        /** 分页查询redis交易数据 */
        TransactionCacheDto transactionCacheDto = statisticCacheService.getTransactionCache(req.getPageNo(), req.getPageSize());
        List<Transaction> items = transactionCacheDto.getTransactionList();
        List<TransactionListResp> lists = this.transferList(items);
        Page<?> page = new Page<>(req.getPageNo(),req.getPageSize());
        result.init(page, lists);
        
        NetworkStat networkStat = statisticCacheService.getNetworkStatCache();
        result.setDisplayTotalCount(transactionCacheDto.getPage().getTotalCount());
        result.setTotalCount(networkStat.getTxQty());
        result.setTotalPages(transactionCacheDto.getPage().getTotalPages());
        return result;
    }

    @Override
    public RespPage<TransactionListResp> getTransactionListByBlock(TransactionListByBlockRequest req) {
        RespPage<TransactionListResp> result = new RespPage<>();
        ESQueryBuilderConstructor constructor = new ESQueryBuilderConstructor();
		constructor.must(new ESQueryBuilders().term("num", req.getBlockNumber()));
		ESResult<Transaction> items = new ESResult<>();
		if (req.getTxType() != null && !req.getTxType().isEmpty()) {
			constructor.must(new ESQueryBuilders().terms("type", ReqTransactionTypeEnum.getTxType(req.getTxType())));
		}
		constructor.setDesc("seq");
		/** 根据区块号和类型分页查询交易信息 */
		try {
			items = transactionESRepository.search(constructor, Transaction.class, req.getPageNo(),req.getPageSize());
		} catch (IOException e) {
			logger.error("获取区块错误。", e);
		}
        List<TransactionListResp> lists = this.transferList(items.getRsData());
        /** 统计交易信息 */
        Page<?> page = new Page<>(req.getPageNo(),req.getPageSize());
        result.init(page, lists);
        result.setTotalCount(items.getTotal());
        return result;
    }

    @Override
    public RespPage<TransactionListResp> getTransactionListByAddress(TransactionListByAddressRequest req) {
        RespPage<TransactionListResp> result = new RespPage<>();
		
		ESQueryBuilderConstructor constructor = new ESQueryBuilderConstructor();
		constructor.should(new ESQueryBuilders().term("from", req.getAddress()));
		constructor.should(new ESQueryBuilders().term("to", req.getAddress()));
		ESResult<Transaction> items = new ESResult<>();
		if (req.getTxType() != null && !req.getTxType().isEmpty()) {
			constructor.must(new ESQueryBuilders().terms("type", ReqTransactionTypeEnum.getTxType(req.getTxType())));
		}
		constructor.setDesc("seq");
		try {
			items = transactionESRepository.search(constructor, Transaction.class, req.getPageNo(),req.getPageSize());
		} catch (IOException e) {
			logger.error("获取区块错误。", e);
		}
        List<TransactionListResp> lists = this.transferList(items.getRsData());
        Page<?> page = new Page<>(req.getPageNo(),req.getPageSize());
        result.init(page, lists);
        result.setTotalCount(items.getTotal());
        return result;
    }

    private List<TransactionListResp> transferList(List<Transaction> items) {
    	List<TransactionListResp> lists = new LinkedList<>();
    	for (Transaction transaction:items) {
        	TransactionListResp transactionListResp = new TransactionListResp();
        	BeanUtils.copyProperties(transaction, transactionListResp);
            transactionListResp.setTxHash(transaction.getHash());
            transactionListResp.setActualTxCost(transaction.getCost().toString());
            transactionListResp.setBlockNumber(transaction.getNum());
            transactionListResp.setReceiveType(String.valueOf(transaction.getToType()));
            transactionListResp.setTxReceiptStatus(transaction.getStatus());
            transactionListResp.setTxType(String.valueOf(transaction.getType()));
            transactionListResp.setValue(transaction.getValue().toString());
            transactionListResp.setServerTime(new Date().getTime());
            transactionListResp.setTimestamp(transaction.getTime().getTime());
            lists.add(transactionListResp);
        }
    	return lists;
    }

    public AccountDownload transactionListByAddressDownload(String address, Long date,String local, String timeZone) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date currentServerTime = new Date();
        String msg = dateFormat.format(currentServerTime);
        logger.info("导出地址交易列表数据起始日期：{},结束日期：{}", date, msg);

        /** 限制最多导出3万条记录 */
		
		ESQueryBuilderConstructor constructor = new ESQueryBuilderConstructor();
		constructor.should(new ESQueryBuilders().term("from", address));
		constructor.should(new ESQueryBuilders().term("to", address));
		constructor.must(new ESQueryBuilders().range("time", new Date(date).getTime(), currentServerTime.getTime()));
		ESResult<Transaction> items = new ESResult<>();
		constructor.setDesc("seq");
		try {
			items = transactionESRepository.search(constructor, Transaction.class, 1, 3000);
		} catch (IOException e) {
			logger.error("获取区块错误。", e);
		}
        List<Object[]> rows = new ArrayList<>();
        items.getRsData().forEach(transaction -> {
        	/**
        	 * 判断是否为to地址
        	 * 如果为to地址则导出报表为收入金额
        	 * 如果为from地址则导出报表为支出金额
        	*/
            boolean toIsAddress = address.equals(transaction.getTo());
            String valueIn = toIsAddress? transaction.getValue().toString() : "0";
            String valueOut = !toIsAddress? transaction.getValue().toString() : "0";
            Object[] row = {
                    transaction.getHash(),
                    transaction.getNum(),
                    DateUtil.timeZoneTransfer(transaction.getTime(), "0", timeZone),
                    i18n.getMessageForStr(Transaction.TypeEnum.getEnum(transaction.getType()).toString(), local),
                    transaction.getFrom(),
                    transaction.getTo(),
                    /** 数值von转换成lat，并保留十八位精确度 */
                    EnergonUtil.format(Convert.fromVon(valueIn, Convert.Unit.LAT).setScale(18,RoundingMode.DOWN), 18),
                    EnergonUtil.format(Convert.fromVon(valueOut, Convert.Unit.LAT).setScale(18,RoundingMode.DOWN), 18),
                    EnergonUtil.format(Convert.fromVon(transaction.getCost(), Convert.Unit.LAT).setScale(18,RoundingMode.DOWN), 18)
            };
            rows.add(row);
        });
        /** 初始化输出流对象 */
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Writer outputWriter = new OutputStreamWriter(byteArrayOutputStream, Charset.defaultCharset());
        try {
        	/** 设置导出的csv头，防止乱码 */
            outputWriter.write(new String(new byte[] { (byte) 0xEF, (byte) 0xBB,(byte) 0xBF }));
        } catch (IOException e) {
        	logger.error("输出数据错误:",e);
        }
        CsvWriter writer = new CsvWriter(outputWriter, new CsvWriterSettings());
        /** 设置导出表的表头 */
        writer.writeHeaders(
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_HASH, local),
                i18n.i(I18nEnum.DOWNLOAD_BLOCK_CSV_NUMBER, local),
                i18n.i(I18nEnum.DOWNLOAD_BLOCK_CSV_TIMESTAMP, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_TYPE, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_FROM, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_TO, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_VALUE_IN, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_VALUE_OUT, local),
                i18n.i(I18nEnum.DOWNLOAD_ACCOUNT_CSV_FEE, local)
        );
        writer.writeRowsAndClose(rows);
        /** 设置返回对象  */
        AccountDownload accountDownload = new AccountDownload();
        accountDownload.setData(byteArrayOutputStream.toByteArray());
        accountDownload.setFilename("Transaction-" + address + "-" + date + ".CSV");
        accountDownload.setLength(byteArrayOutputStream.size());
        return accountDownload;
    }

    @Override
    public TransactionDetailsResp transactionDetails( TransactionDetailsReq req) {
    	/** 根据hash查询具体的交易数据 */
		ESQueryBuilderConstructor constructor = new ESQueryBuilderConstructor();
		constructor.must(new ESQueryBuilders().term("hash", req.getTxHash()));
		ESResult<Transaction> items = new ESResult<>();
		try {
			items = transactionESRepository.search(constructor, Transaction.class, 1, 1);
		} catch (IOException e) {
			logger.error("获取区块错误。", e);
		}
    	TransactionDetailsResp resp = new TransactionDetailsResp();
    	if(items!=null && items.getRsData().size() > 0) {
    		Transaction transaction = items.getRsData().get(0);
    		BeanUtils.copyProperties(transaction, resp);
    		resp.setActualTxCost(transaction.getCost().toString());
    		resp.setBlockNumber(transaction.getNum());
    		resp.setGasLimit(transaction.getGasLimit().toString());
    		resp.setGasPrice(transaction.getGasPrice().toString());
    		resp.setGasUsed(transaction.getGasUsed().toString());
    		resp.setReceiveType(String.valueOf(transaction.getToType()));
    		resp.setTxReceiptStatus(transaction.getStatus());
    		resp.setTxType(String.valueOf(transaction.getType()));
    		resp.setValue(transaction.getValue().toString());
    		resp.setTxHash(transaction.getHash());
    		resp.setTimestamp(transaction.getTime().getTime());
    		resp.setServerTime(new Date().getTime());
    		List<Block> blocks = statisticCacheService.getBlockCache(0, 1);
    		/** 确认区块数等于当前区块书减去交易区块数  */
    		if(!blocks.isEmpty()) {
    			resp.setConfirmNum(String.valueOf(blocks.get(0).getNum()-transaction.getNum()));
    		} else {
    			resp.setConfirmNum("0");
			}

    		/** 暂时只有账户合约 */
    		resp.setReceiveType("account");
    		/** 如果数据值为null 则置为空 */
    		if("null".equals(transaction.getInfo())) {
    			resp.setTxInfo("0x");
    		}
    		/*
    		 * "first":false,            //是否第一条记录
    		 * "last":true,              //是否最后一条记录
    		 */
    		resp.setFirst(false);
    		if(transaction.getId().longValue() == 1l) {
    			resp.setFirst(true);
    		} else {
    			/** 根据id查询具体的交易数据 */
        		constructor = new ESQueryBuilderConstructor();
        		constructor.must(new ESQueryBuilders().term("id", transaction.getId()-1));
        		ESResult<Transaction> first = new ESResult<>();
        		try {
        			first = transactionESRepository.search(constructor, Transaction.class, 1, 1);
        		} catch (IOException e) {
        			logger.error("获取交易错误。", e);
        		}
        		resp.setPreHash(first.getRsData().get(0).getHash());
    		}
    		
    		resp.setLast(true);
    		/** 根据id查询具体的交易数据 */
    		constructor = new ESQueryBuilderConstructor();
    		constructor.must(new ESQueryBuilders().term("id", transaction.getId()+1));
    		ESResult<Transaction> last = new ESResult<>();
    		try {
    			last = transactionESRepository.search(constructor, Transaction.class, 1, 1);
    		} catch (IOException e) {
    			logger.error("获取交易错误。", e);
    		}
    		if(last.getTotal().longValue() > 0l) {
    			resp.setLast(false);
    			resp.setNextHash(last.getRsData().get(0).getHash());
    		}
    		
    		String txInfo = transaction.getInfo();
    		/** 根据不同交易类型判断逻辑 */
    		if(StringUtils.isNotBlank(txInfo) || (!"null".equals(txInfo))) {
	    		switch (Transaction.TypeEnum.getEnum(transaction.getType())) {
		    		/** 创建验证人 */
					case STAKE_CREATE:
						StakeCreateParam createValidatorParam = JSONObject.parseObject(txInfo, StakeCreateParam.class);
						resp.setTxAmount(createValidatorParam.getAmount().toString());
						resp.setBenefitAddr(createValidatorParam.getBenefitAddress());
						resp.setNodeId(createValidatorParam.getNodeId());
						resp.setNodeName(createValidatorParam.getNodeName());
						resp.setExternalId(createValidatorParam.getExternalId());
						resp.setWebsite(createValidatorParam.getWebsite());
						resp.setDetails(createValidatorParam.getDetails());
						resp.setProgramVersion(createValidatorParam.getProgramVersion().toString());
						resp.setTxAmount(createValidatorParam.getAmount().toString());
						resp.setExternalUrl(this.getStakingUrl(createValidatorParam.getExternalId(), resp.getTxReceiptStatus()));
						break;
					//编辑验证人
					case STAKE_MODIFY:
						StakeModifyParam editValidatorParam = JSONObject.parseObject(txInfo, StakeModifyParam.class);
						resp.setBenefitAddr(editValidatorParam.getBenefitAddress());
						resp.setNodeId(editValidatorParam.getNodeId());
						resp.setExternalId(editValidatorParam.getExternalId());
						resp.setWebsite(editValidatorParam.getWebsite());
						resp.setDetails(editValidatorParam.getDetails());
						resp.setNodeName(this.setStakingName(editValidatorParam.getNodeId(), editValidatorParam.getNodeName()));
						resp.setExternalUrl(this.getStakingUrl(editValidatorParam.getExternalId(), resp.getTxReceiptStatus()));
						break;
					//增加质押
					case STAKE_INCREASE:
						StakeIncreaseParam increaseStakingParam = JSONObject.parseObject(txInfo, StakeIncreaseParam.class);
						resp.setNodeId(increaseStakingParam.getNodeId());
						resp.setTxAmount(increaseStakingParam.getAmount().toString());
						resp.setNodeName(this.setStakingName(increaseStakingParam.getNodeId(), increaseStakingParam.getNodeName()));
						break;
					//退出验证人
					case STAKE_EXIT:
						// nodeId + nodeName + applyAmount + redeemLocked + redeemStatus + redeemUnLockedBlock
						StakeExitParam exitValidatorParam = JSONObject.parseObject(txInfo, StakeExitParam.class);
						resp.setNodeId(exitValidatorParam.getNodeId());
						resp.setNodeName(this.setStakingName(exitValidatorParam.getNodeId(), exitValidatorParam.getNodeName()));
						resp.setApplyAmount(exitValidatorParam.getAmount().toString());
						StakingKey stakingKeyE = new StakingKey();
						stakingKeyE.setNodeId(exitValidatorParam.getNodeId());
						if(exitValidatorParam.getStakingBlockNum()==null) {
							stakingKeyE.setStakingBlockNum(exitValidatorParam.getStakingBlockNum().longValue());
						}
						Staking staking = stakingMapper.selectByPrimaryKey(stakingKeyE);
						if(staking!=null) {
							resp.setRedeemLocked(staking.getStakingReduction().toString());
							//只有已退出，则金额才会退回到账户
							if(staking.getStatus() == CustomStaking.StatusEnum.EXITED.getCode()) {
								resp.setRedeemStatus(RedeemStatusEnum.EXITED.getCode());
							} else {
								resp.setRedeemStatus(RedeemStatusEnum.EXITING.getCode());
							}
							//（staking_reduction_epoch  + 节点质押退回锁定周期） * 结算周期区块数(C) + 现有区块数
							BigDecimal blockNum = (new BigDecimal(staking.getStakingReductionEpoch()).add(new BigDecimal(blockChainConfig.getUnStakeRefundSettlePeriodCount())))
									.multiply(new BigDecimal(blockChainConfig.getSettlePeriodBlockCount())).add(new BigDecimal(resp.getBlockNumber()));
							resp.setRedeemUnLockedBlock(blockNum.toString());
						}
						break;
						//委托
					case DELEGATE_CREATE:
						DelegateCreateParam delegateParam = JSONObject.parseObject(txInfo, DelegateCreateParam.class);
						resp.setNodeId(delegateParam.getNodeId());
						resp.setTxAmount(delegateParam.getAmount().toString());
						resp.setNodeName(this.setStakingName(delegateParam.getNodeId(), delegateParam.getNodeName()));
						break;
					//委托赎回
					case DELEGATE_EXIT:
						// nodeId + nodeName + applyAmount + redeemLocked + redeemStatus
						// 通过txHash关联un_delegation表
						DelegateExitParam unDelegateParam = JSONObject.parseObject(txInfo, DelegateExitParam.class);
						resp.setNodeId(unDelegateParam.getNodeId());
						resp.setApplyAmount(unDelegateParam.getAmount().toString());
						resp.setTxAmount(unDelegateParam.getAmount().toString());
						resp.setNodeName(this.setStakingName(unDelegateParam.getNodeId(), unDelegateParam.getNodeName()));
//						UnDelegation unDelegation = unDelegationMapper.selectByPrimaryKey(req.getTxHash());
//						if(unDelegation!=null) {
//							resp.setApplyAmount(unDelegation.getApplyAmount());
//							resp.setRedeemLocked(unDelegation.getRedeemLocked());
//							resp.setRedeemStatus(unDelegation.getStatus());
//						}
						break;
					case PROPOSAL_TEXT:
						ProposalTextParam createProposalTextParam = JSONObject.parseObject(txInfo, ProposalTextParam.class);
						if(StringUtils.isNotBlank(createProposalTextParam.getPIDID())) {
							resp.setPipNum("PIP-" + createProposalTextParam.getPIDID());
						}
						resp.setNodeId(createProposalTextParam.getVerifier());
						resp.setProposalHash(req.getTxHash());
						resp.setNodeName(this.setStakingName(createProposalTextParam.getVerifier(), createProposalTextParam.getNodeName()));
						/** 如果数据库有值，以数据库为准 */
						this.transferTransaction(resp, req.getTxHash());
						break;
					case PROPOSAL_UPGRADE:
						ProposalUpgradeParam createProposalUpgradeParam = JSONObject.parseObject(txInfo, ProposalUpgradeParam.class);
						resp.setProposalNewVersion(String.valueOf(createProposalUpgradeParam.getNewVersion()));
						if(StringUtils.isNotBlank(createProposalUpgradeParam.getPIDID())) {
							resp.setPipNum("PIP-" + createProposalUpgradeParam.getPIDID());
						}
						resp.setNodeId(createProposalUpgradeParam.getVerifier());
						resp.setProposalHash(req.getTxHash());
						resp.setNodeName(this.setStakingName(createProposalUpgradeParam.getVerifier(), createProposalUpgradeParam.getNodeName()));
						/** 如果数据库有值，以数据库为准 */
						this.transferTransaction(resp, req.getTxHash());
						break;
					case PROPOSAL_PARAMETER:
					case PROPOSAL_CANCEL:
						ProposalCancelParam cancelProposalParam = JSONObject.parseObject(txInfo, ProposalCancelParam.class);
						if(StringUtils.isNotBlank(cancelProposalParam.getPIDID())) {
							resp.setPipNum("PIP-" + cancelProposalParam.getPIDID());
						}
						resp.setNodeId(cancelProposalParam.getVerifier());
						resp.setProposalHash(req.getTxHash());
						resp.setNodeName(this.setStakingName(cancelProposalParam.getVerifier(), cancelProposalParam.getNodeName()));
						/** 如果数据库有值，以数据库为准 */
						this.transferTransaction(resp, req.getTxHash());
						break;
					case PROPOSAL_VOTE:
						// nodeId + nodeName + txType + proposalUrl + proposalHash + proposalNewVersion +  proposalOption
						ProposalVoteParam votingProposalParam = JSONObject.parseObject(txInfo, ProposalVoteParam.class);
						resp.setNodeId(votingProposalParam.getVerifier());
						resp.setProposalOption(votingProposalParam.getProposalType());
						resp.setProposalHash(votingProposalParam.getProposalId());
						resp.setProposalNewVersion(votingProposalParam.getProgramVersion());
						resp.setNodeName(this.setStakingName(votingProposalParam.getVerifier(), votingProposalParam.getNodeName()));
						if(StringUtils.isNotBlank(votingProposalParam.getPIDID())) {
							resp.setPipNum("PIP-" + votingProposalParam.getPIDID());
						}
						resp.setVoteStatus(votingProposalParam.getOption());
						CustomVoteProposal customVoteProposal = customVoteMapper.selectVotePropal(req.getTxHash());
						if(customVoteProposal != null) {
							resp.setNodeId(customVoteProposal.getNodeId());
							resp.setNodeName(customVoteProposal.getNodeName());
							resp.setProposalOption(customVoteProposal.getType());
							resp.setProposalHash(customVoteProposal.getProposalHash());
							resp.setProposalNewVersion(customVoteProposal.getNewVersion());
							resp.setPipNum(customVoteProposal.getPipNum());
							resp.setProposalTitle(customVoteProposal.getTopic());
							resp.setProposalUrl(customVoteProposal.getUrl());
							resp.setVoteStatus(String.valueOf(customVoteProposal.getOption()));
						}
						/**
						 * 失败情况下需要到提案上获取提案信息
						 */
						if(resp.getTxReceiptStatus() == Transaction.StatusEnum.FAILURE.getCode()) {
							Proposal proposal = proposalMapper.selectByPrimaryKey(votingProposalParam.getProposalId());
							if(proposal != null) {
								resp.setPipNum(proposal.getPipNum());
								resp.setProposalTitle(proposal.getTopic());
								resp.setProposalUrl(proposal.getUrl());
								resp.setProposalOption(String.valueOf(proposal.getType()));
							}
						}
						break;
						//版本申明
					case VERSION_DECLARE:
						VersionDeclareParam declareVersionParam = JSONObject.parseObject(txInfo, VersionDeclareParam.class);
						resp.setNodeId(declareVersionParam.getActiveNode());
						resp.setDeclareVersion(String.valueOf(declareVersionParam.getVersion()));
						resp.setNodeName(this.setStakingName(declareVersionParam.getActiveNode(), declareVersionParam.getNodeName()));
						if(StringUtils.isNotBlank(declareVersionParam.getNodeName())) {
							resp.setNodeName(declareVersionParam.getNodeName());
						} else {
							StakingExample stakingExample = new StakingExample();
							stakingExample.setOrderByClause(" staking_block_num desc");
							stakingExample.createCriteria().andNodeIdEqualTo(declareVersionParam.getActiveNode());
							List<Staking> stakings = stakingMapper.selectByExample(stakingExample);
							if(!stakings.isEmpty()) {
								resp.setNodeName(stakings.get(0).getNodeName());
							}
						}
						break;
					case REPORT:
						ReportParam reportValidatorParam = JSONObject.parseObject(txInfo, ReportParam.class);
						List<TransactionDetailsEvidencesResp> transactionDetailsEvidencesResps = new ArrayList<>();
						TransactionDetailsEvidencesResp transactionDetailsEvidencesResp = new TransactionDetailsEvidencesResp();
						transactionDetailsEvidencesResp.setVerify(reportValidatorParam.getVerify());
						transactionDetailsEvidencesResp.setNodeName(this.setStakingName(reportValidatorParam.getVerify(), reportValidatorParam.getNodeName()));
						resp.setEvidence(reportValidatorParam.getData());
						transactionDetailsEvidencesResps.add(transactionDetailsEvidencesResp);
						Slash slash = slashMapper.selectByPrimaryKey(req.getTxHash());
						if(slash != null) {
							resp.setReportRewards(slash.getReward().toString());
							resp.setReportStatus(slash.getIsQuit());
						}
						resp.setReportType(reportValidatorParam.getType().intValue());
						resp.setEvidences(transactionDetailsEvidencesResps);
						break;
					case RESTRICTING_CREATE:
						// RPAccount + value + RPPlan
						RestrictingCreateParam createRestrictingParam = JSONObject.parseObject(txInfo, RestrictingCreateParam.class);
						List<TransactionDetailsRPPlanResp> rpPlanResps = new ArrayList<>();
						resp.setRPAccount(createRestrictingParam.getAccount());
						BigDecimal amountSum = new BigDecimal(0);
						for(RestrictingCreateParam.RestrictingPlan p:createRestrictingParam.getPlans()) {
							TransactionDetailsRPPlanResp transactionDetailsRPPlanResp = new TransactionDetailsRPPlanResp();
							amountSum = amountSum.add(p.getAmount());
							transactionDetailsRPPlanResp.setAmount(p.getAmount().toString());
							transactionDetailsRPPlanResp.setEpoch(p.getEpoch());
							//锁仓周期对应快高  结算周期 * epoch
							transactionDetailsRPPlanResp.setBlockNumber(blockChainConfig.getSettlePeriodBlockCount()
									.multiply(new BigInteger(String.valueOf(p.getEpoch()))).longValue());
							rpPlanResps.add(transactionDetailsRPPlanResp);
						}
						//累加
						resp.setRPNum(amountSum.toString());
						resp.setRPPlan(rpPlanResps);
						break;
				default:
					break;
				}
    		}
    	}
    	return resp;
    }

    /**
     * 提案信息统一转换
     * @method transferTransaction
     * @param resp
     * @param hash
     * @return
     */
    private TransactionDetailsResp transferTransaction(TransactionDetailsResp resp, String hash) {
    	Proposal proposal = proposalMapper.selectByPrimaryKey(hash);
		if(proposal != null) {
			resp.setNodeId(proposal.getNodeId());
			resp.setNodeName(proposal.getNodeName());
			resp.setPipNum(proposal.getPipNum());
			resp.setProposalTitle(proposal.getTopic());
			resp.setProposalStatus(proposal.getStatus());
			resp.setProposalOption(String.valueOf(proposal.getType()));
			resp.setProposalNewVersion(proposal.getNewVersion());
			resp.setProposalUrl(proposal.getUrl());
		}
		return resp;
    }

    /**
     * 统一设置验证人名称
     * @method setStakingName
     * @param nodeId
     * @param nodeName
     * @return
     */
    private String setStakingName(String nodeId,String nodeName) {
    	/**
    	 * 当nodeId为空或者nodeName不为空则直接返回name
    	 */
    	if(StringUtils.isNotBlank(nodeName) || StringUtils.isBlank(nodeId)) {
    		return nodeName;
    	}
    	StakingExample stakingExample = new StakingExample();
    	stakingExample.setOrderByClause(" staking_block_num desc");
    	StakingExample.Criteria criteria = stakingExample.createCriteria();
    	criteria.andNodeIdEqualTo(nodeId);
    	List<Staking> stakings = stakingMapper.selectByExample(stakingExample);
    	if(stakings != null && !stakings.isEmpty()) {
    		return stakings.get(0).getNodeName();
    	}
    	return nodeName;
    }

    /**
     * 统一设置验证人keybaseurl
     * @method getStakingUrl
     * @param externalId
     * @param txReceiptStatus
     * @return
     */
    private String getStakingUrl(String externalId,Integer txReceiptStatus) {

    	String keyBaseUrl = blockChainConfig.getKeyBase();
        String keyBaseApi = blockChainConfig.getKeyBaseApi();
        String defaultBaseUrl = blockChainConfig.getKeyBase();
    	/**
		 * 如果externalId为空就不返回给前端url，反转跳转
		 */
		if(StringUtils.isNotBlank(externalId)) {
			/**
			 * 如果为失败的交易直接设置默认的url然后跳出
			 */
			if(txReceiptStatus == Transaction.StatusEnum.FAILURE.getCode()) {
				return defaultBaseUrl;
			}

			String url = keyBaseUrl.concat(keyBaseApi.concat(externalId));
            String userName = "";
			try {
				KeyBaseUser keyBaseUser = HttpUtil.get(url,KeyBaseUser.class);
				userName = KeyBaseAnalysis.getKeyBaseUseName(keyBaseUser);
			} catch (Exception e) {
				logger.error("getStakingUrl error.externalId:{},txReceiptStatus:{},error:{}",externalId, txReceiptStatus, e.getMessage());
				return defaultBaseUrl;
			}
			if(StringUtils.isNotBlank(userName)) {
				defaultBaseUrl += userName;
			}
			return defaultBaseUrl;
		}
    	return null;
    }

}
