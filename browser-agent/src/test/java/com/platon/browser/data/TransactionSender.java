package com.platon.browser.data;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.platon.BaseResponse;
import org.web3j.platon.StakingAmountType;
import org.web3j.platon.bean.Node;
import org.web3j.platon.contracts.DelegateContract;
import org.web3j.platon.contracts.NodeContract;
import org.web3j.platon.contracts.RestrictingPlanContract;
import org.web3j.platon.contracts.StakingContract;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ChainId;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultWasmGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * @Auther: Chendongming
 * @Date: 2019/8/15 20:58
 * @Description:
 */
public class TransactionSender {
	private static String chainId = "100";
    private static Logger logger = LoggerFactory.getLogger(TransactionSender.class);
    private Web3j currentValidWeb3j = Web3j.build(new HttpService("http://192.168.112.171:6789"));
    private Credentials credentials = Credentials.create("00a56f68ca7aa51c24916b9fff027708f856650f9ff36cc3c8da308040ebcc7867");
    NodeContract nodeContract = NodeContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider());
    StakingContract stakingContract = StakingContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),chainId);
    DelegateContract delegateContract = DelegateContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),chainId);
    RestrictingPlanContract restrictingPlanContract = RestrictingPlanContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),chainId);
    public TransactionSender() throws IOException, CipherException {}

    // 发送转账交易
    @Test
    public void transfer() throws Exception {
    	for(int i=0; i < 30;i++) {
	        Web3j web3j = Web3j.build(new HttpService("http://192.168.112.171:6789"));
	        logger.error("{}",Hex.toHexString(credentials.getEcKeyPair().getPrivateKey().toByteArray()));
	        BigInteger blockNumber = web3j.platonBlockNumber().send().getBlockNumber();
	        Transfer.sendFunds(
	                web3j,
	                credentials,
	                chainId,
	                "0x8b77ac9fabb6fe247ee91ca07ea4f62c6761e79b",
	                BigDecimal.valueOf(100),
	                Convert.Unit.VON
	        ).send();
	        BigInteger balance = web3j.platonGetBalance("0x8b77ac9fabb6fe247ee91ca07ea4f62c6761e79b", DefaultBlockParameterName.LATEST).send().getBalance();
	        logger.debug("balance:{}",balance);
    	}
    }

    // 发送质押交易
    @Test
    public void staking() throws Exception {
    	BigDecimal bigDecimal = Convert.toVon("5000000", Unit.LAT);
        BaseResponse res = stakingContract.staking(
                "0x00cc251cf6bf3ea53a748971a223f5676225ee4380b65c7889a2b491e1551d45fe9fcc19c6af54dcf0d5323b5aa8ee1d919791695082bae1f86dd282dba41000",
                bigDecimal.toBigInteger(),
                StakingAmountType.FREE_AMOUNT_TYPE,
                "0x60ceca9c1290ee56b98d4e160ef0453f7c40d219",
                "",
                "cdm",
                "www.baidu.com",
                "baidu",
                BigInteger.valueOf(70)
        ).send();
        logger.debug("res:{}",res);
    }

    // 修改质押信息(编辑验证人)
    @Test
    public void updateStakingInfo() throws Exception {
        StakingContract stakingContract = StakingContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),"101");
        BaseResponse res = stakingContract.updateStakingInfo(
                "0x00cc251cf6bf3ea53a748971a223f5676225ee4380b65c7889a2b491e1551d45fe9fcc19c6af54dcf0d5323b5aa8ee1d919791695082bae1f86dd282dba41000",
                "0x60ceca9c1290ee56b98d4e160ef0453f7c40d219",
                "PID-002","cdm-004","WWW.CCC.COM","Node of CDM"
        ).send();
        logger.debug("res:{}",res);
    }

    // 增持质押(增加自有质押)
    @Test
    public void addStaking() throws Exception {
        StakingContract stakingContract = StakingContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),"101");
        BaseResponse res = stakingContract.addStaking(
                "0x00cc251cf6bf3ea53a748971a223f5676225ee4380b65c7889a2b491e1551d45fe9fcc19c6af54dcf0d5323b5aa8ee1d919791695082bae1f86dd282dba41000",
                StakingAmountType.FREE_AMOUNT_TYPE,
                BigInteger.valueOf(555)
        ).send();
        logger.debug("res:{}",res);
    }

    // 撤销质押(退出验证人)
    @Test
    public void unStaking() throws Exception {
        StakingContract stakingContract = StakingContract.load(currentValidWeb3j,credentials,new DefaultWasmGasProvider(),"101");
        BaseResponse res = stakingContract.unStaking(
                "0x00cc251cf6bf3ea53a748971a223f5676225ee4380b65c7889a2b491e1551d45fe9fcc19c6af54dcf0d5323b5aa8ee1d919791695082bae1f86dd282dba41000"
        ).send();
        logger.debug("res:{}",res);
    }

    // 发送委托交易
    @Test
    public void delegate() throws Exception {
        BaseResponse res = delegateContract.delegate(
                "0x00cc251cf6bf3ea53a748971a223f5676225ee4380b65c7889a2b491e1551d45fe9fcc19c6af54dcf0d5323b5aa8ee1d919791695082bae1f86dd282dba41000",
                StakingAmountType.FREE_AMOUNT_TYPE,
                BigInteger.valueOf(1000)
        ).send();
        logger.debug("res:{}",res);
    }
    
 // 发送委托交易
    @Test
    public void restricting() throws Exception {
    	
    	BaseResponse res = restrictingPlanContract.createRestrictingPlan("", null).send();
        logger.debug("res:{}",res);
    }
    
    public static void main(String[] args) throws IOException, CipherException {
    	Credentials credentials = WalletUtils.loadCredentials("88888888", "F:\\文件\\矩真文件\\区块链\\PlatScan\\fd9d508df262a1c968e0d6c757ab08b96d741f4b_88888888.json");
    	byte[] byteArray = credentials.getEcKeyPair().getPrivateKey().toByteArray();
        String privateKey = Hex.toHexString(byteArray);

        logger.debug("Private Key:{}",privateKey);
	}
    
}
