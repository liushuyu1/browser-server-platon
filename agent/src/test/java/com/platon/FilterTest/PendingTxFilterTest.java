package com.platon.FilterTest;

import com.platon.browser.SpringbootApplication;
import com.platon.browser.client.Web3jClient;
import com.platon.browser.filter.PendingFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthPendingTransactions;

/**
 * User: dongqile
 * Date: 2019/1/11
 * Time: 15:40
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes= SpringbootApplication.class, value = "spring.profiles.active=1")
public class PendingTxFilterTest {

    private static Logger logger = LoggerFactory.getLogger(PendingTxFilterTest.class);

    @Autowired
    private Web3jClient web3jClient;

    @Autowired
    private PendingFilter pendingFilter;

    @Test
    public void PengdingTxFilterTest(){
        try {
            Web3j web3j = web3jClient.getWeb3jClient();
            EthPendingTransactions ethPendingTransactions = web3j.ethPendingTx().send();
            pendingFilter.PendingFilter(ethPendingTransactions);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}