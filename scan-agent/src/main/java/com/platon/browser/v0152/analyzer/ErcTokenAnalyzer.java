package com.platon.browser.v0152.analyzer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.platon.browser.bean.CollectionTransaction;
import com.platon.browser.bean.Receipt;
import com.platon.browser.cache.AddressCache;
import com.platon.browser.dao.entity.Token;
import com.platon.browser.dao.mapper.CustomTokenMapper;
import com.platon.browser.elasticsearch.dto.Block;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.exception.TokenException;
import com.platon.browser.utils.AddressUtil;
import com.platon.browser.utils.CommonUtil;
import com.platon.browser.v0152.bean.ErcContractId;
import com.platon.browser.v0152.bean.ErcToken;
import com.platon.browser.v0152.bean.ErcTxInfo;
import com.platon.browser.v0152.contract.ErcContract;
import com.platon.browser.v0152.enums.ErcTypeEnum;
import com.platon.browser.v0152.service.ErcDetectService;
import com.platon.protocol.core.methods.response.Log;
import com.platon.protocol.core.methods.response.TransactionReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Erc Token 服务
 */
@Slf4j
@Service
public class ErcTokenAnalyzer {

    @Resource
    private ErcDetectService ercDetectService;

    @Resource
    private ErcCache ercCache;

    @Resource
    private AddressCache addressCache;

    @Resource
    private ErcTokenInventoryAnalyzer ercTokenInventoryAnalyzer;

    @Resource
    private ErcTokenHolderAnalyzer ercTokenHolderAnalyzer;

    @Resource
    private CustomTokenMapper customTokenMapper;

    /**
     * 解析Token,在合约创建时调用
     *
     * @param contractAddress
     */
    public ErcToken resolveToken(String contractAddress) {
        ErcToken token = new ErcToken();
        token.setTypeEnum(ErcTypeEnum.UNKNOWN);
        try {
            token.setAddress(contractAddress);
            ErcContractId contractId = ercDetectService.getContractId(contractAddress);
            BeanUtils.copyProperties(contractId, token);
            token.setTypeEnum(contractId.getTypeEnum());
            token.setType(contractId.getTypeEnum().name().toLowerCase());
            switch (contractId.getTypeEnum()) {
                case ERC20:
                    token.setIsSupportErc20(true);
                    token.setIsSupportErc165(false);
                    token.setIsSupportErc721(false);
                    token.setIsSupportErc721Enumeration(token.getIsSupportErc721());
                    token.setIsSupportErc721Metadata(token.getIsSupportErc721());
                    ercCache.erc20AddressCache.add(contractAddress);
                    break;
                case ERC721:
                    token.setIsSupportErc20(false);
                    token.setIsSupportErc165(true);
                    token.setIsSupportErc721(true);
                    token.setIsSupportErc721Enumeration(ercDetectService.isSupportErc721Enumerable(contractAddress));
                    token.setIsSupportErc721Metadata(ercDetectService.isSupportErc721Metadata(contractAddress));
                    ercCache.erc721AddressCache.add(contractAddress);
                    break;
                default:
            }
            if (token.getTypeEnum() != ErcTypeEnum.UNKNOWN) {
                // 入库ERC721或ERC20 Token记录
                token.setTokenTxQty(0);
                // 检查token是否合法
                checkToken(token);
                customTokenMapper.batchInsertOrUpdateSelective(Collections.singletonList(token), Token.Column.values());
                ercCache.tokenCache.put(token.getAddress(), token);
                log.info("创建合约成功，合约地址为[{}],合约类型为[{}]", token.getAddress(), token.getType());
            } else {
                log.error("该合约地址[{}]无法识别该类型[{}]", token.getAddress(), token.getTypeEnum());
            }
        } catch (Exception e) {
            log.error("合约创建,解析Token异常", e);
        }
        return token;
    }

    /**
     * token校验---根据mysql定义字段来约束校验
     *
     * @param token 合约
     * @return void
     * @date 2021/4/29
     */
    private void checkToken(ErcToken token) {
        // 1.校验地址长度，约束为64位
        int tokenAddressLength = CommonUtil.ofNullable(() -> token.getAddress().length()).orElse(0);
        if (tokenAddressLength > 64) {
            throw new TokenException("token地址长度过长", token);
        }
        // 2.校验合约名称，可以为null，约束为64
        if (StrUtil.isNotEmpty(token.getName())) {
            // 校验合约名称长度，默认为64
            if (CommonUtil.ofNullable(() -> token.getName().length()).orElse(0) > 64) {
                String name = StrUtil.fillAfter(StrUtil.sub(token.getName(), 0, 61), '.', 64);
                log.warn("该token[{}]的名称过长（默认64位）,将自动截取,旧值[{}],新值[{}]", token.getAddress(), token.getName(), name);
                token.setName(name);
            }
        }
        // 3.校验合约符号，可以为nul，约束为64位
        if (StrUtil.isNotEmpty(token.getSymbol())) {
            if (CommonUtil.ofNullable(() -> token.getSymbol().length()).orElse(0) > 64) {
                throw new TokenException("token合约符号过长", token);
            }
        }
        // 4.校验供应总量，可以为nul，约束为decimal(64,0)
        if (ObjectUtil.isNotNull(token.getTotalSupply())) {
            // 转成字符串在处理
            String[] totalSupply = StrUtil.split(CommonUtil.ofNullable(() -> token.getTotalSupply().toString()).orElse(""), ".");
            // 整数部分不能超过64位
            if (CommonUtil.ofNullable(() -> totalSupply[0].length()).orElse(0) > 64) {
                throw new TokenException("token供应总量过大,仅支持整数位数64位", token);
            }
            if (totalSupply.length > 1) {
                // 不能有小数
                if (CommonUtil.ofNullable(() -> totalSupply[1].length()).orElse(0) > 0) {
                    throw new TokenException("token供应总量不支持小数", token);
                }
            }
        }
        // 5.校验合约精度，可以为null，约束为11位
        if (ObjectUtil.isNotNull(token.getDecimal())) {
            if (CommonUtil.ofNullable(() -> token.getDecimal().toString().length()).orElse(0) > 11) {
                throw new TokenException("token合约精度过长，仅支持11位", token);
            }
        }
    }

    /**
     * 从交易回执的事件中解析出交易
     *
     * @param token     token
     * @param tx        交易
     * @param eventList 事件列表
     * @return java.util.List<com.platon.browser.elasticsearch.dto.ErcTx> erc交易列表
     * @date 2021/4/14
     */
    private List<ErcTx> resolveErcTxFromEvent(Token token, CollectionTransaction tx, List<ErcContract.ErcTxEvent> eventList, Long seq) {
        List<ErcTx> txList = new ArrayList<>();
        eventList.forEach(event -> {
            // 转换参数进行设置内部交易
            ErcTx ercTx = ErcTx.builder()
                    .seq(seq)
                    .bn(tx.getNum())
                    .hash(tx.getHash())
                    .bTime(tx.getTime())
                    .txFee(tx.getCost())
                    .fromType(addressCache.getTypeData(event.getFrom()))
                    .toType(addressCache.getTypeData(event.getTo()))
                    .from(event.getFrom())
                    .to(event.getTo())
                    .value(event.getValue().toString())
                    .name(token.getName())
                    .symbol(token.getSymbol())
                    .decimal(token.getDecimal())
                    .contract(token.getAddress())
                    .build();
            txList.add(ercTx);
        });
        return txList;
    }

    /**
     * 获取交易信息
     *
     * @param txList 交易列表
     * @return java.lang.String
     * @date 2021/4/14
     */
    private String getErcTxInfo(List<ErcTx> txList) {
        List<ErcTxInfo> infoList = new ArrayList<>();
        txList.forEach(tx -> {
            ErcTxInfo eti = new ErcTxInfo();
            BeanUtils.copyProperties(tx, eti);
            infoList.add(eti);
        });
        return JSON.toJSONString(infoList);
    }

    /**
     * 解析ERC交易, 在合约调用时调用
     *
     * @param collectionBlock 当前区块
     * @param tx              交易对象
     * @param receipt         交易回执：一笔交易可能包含多个事件，故可能有多条交易
     * @return void
     * @date 2021/4/15
     */
    public void resolveTx(Block collectionBlock, CollectionTransaction tx, Receipt receipt) {
        try {
            // 过滤交易回执日志，地址不能为空且在token缓存里的
            List<Log> tokenLogs = receipt.getLogs().stream()
                    .filter(receiptLog -> StrUtil.isNotEmpty(receiptLog.getAddress()))
                    .filter(receiptLog -> ercCache.tokenCache.containsKey(receiptLog.getAddress()))
                    .collect(Collectors.toList());

            if (CollUtil.isEmpty(tokenLogs)) {
                return;
            }

            tokenLogs.forEach(tokenLog -> {
                ErcToken token = ercCache.tokenCache.get(tokenLog.getAddress());
                if (ObjectUtil.isNotNull(token)) {
                    List<ErcTx> txList = Collections.emptyList();
                    String contractAddress = token.getAddress();
                    ErcTypeEnum typeEnum = ErcTypeEnum.valueOf(token.getType().toUpperCase());
                    TransactionReceipt transactionReceipt = new TransactionReceipt();
                    transactionReceipt.setLogs(receipt.getLogs());
                    transactionReceipt.setContractAddress(contractAddress);
                    List<ErcContract.ErcTxEvent> eventList;
                    switch (typeEnum) {
                        case ERC20:
                            eventList = ercDetectService.getErc20TxEvents(transactionReceipt);
                            List<ErcContract.ErcTxEvent> erc20TxEventList = eventList.stream().filter(v -> ObjectUtil.equal(v.getLog(), tokenLog)).collect(Collectors.toList());
                            if (erc20TxEventList.size() > 1) {
                                log.error("当前交易[{}]erc20交易回执日志解析异常{}", tx.getHash(), tokenLog);
                                break;
                            }
                            txList = resolveErcTxFromEvent(token, tx, erc20TxEventList, collectionBlock.getSeq().incrementAndGet());
                            tx.getErc20TxList().addAll(txList);
                            break;
                        case ERC721:
                            eventList = ercDetectService.getErc721TxEvents(transactionReceipt);
                            List<ErcContract.ErcTxEvent> erc721TxEventList = eventList.stream().filter(v -> v.getLog().equals(tokenLog)).collect(Collectors.toList());
                            if (erc721TxEventList.size() > 1) {
                                log.error("当前交易[{}]erc721交易回执日志解析异常{}", tx.getHash(), tokenLog);
                                break;
                            }
                            txList = resolveErcTxFromEvent(token, tx, erc721TxEventList, collectionBlock.getSeq().incrementAndGet());
                            tx.getErc721TxList().addAll(txList);
                            ercTokenInventoryAnalyzer.analyze(tx.getHash(), txList);
                            break;
                    }
                    token.setTokenTxQty(token.getTokenTxQty() + txList.size());
                    token.setUpdateTime(new Date());
                    token.setDirty(true);
                    ercTokenHolderAnalyzer.analyze(txList);
                    // 以上所有操作无误，最后更新地址表erc交易数缓存
                    txList.forEach(ercTx -> {
                        if (!AddressUtil.isAddrZero(ercTx.getFrom())) {
                            switch (typeEnum) {
                                case ERC20:
                                    addressCache.updateErc20TxQty(ercTx.getFrom());
                                    break;
                                case ERC721:
                                    addressCache.updateErc721TxQty(ercTx.getFrom());
                                    break;
                            }
                        }
                        if (!AddressUtil.isAddrZero(ercTx.getTo())) {
                            switch (typeEnum) {
                                case ERC20:
                                    addressCache.updateErc20TxQty(ercTx.getTo());
                                    break;
                                case ERC721:
                                    addressCache.updateErc721TxQty(ercTx.getTo());
                                    break;
                            }
                        }
                    });
                } else {
                    log.error("当前交易[{}]缓存中未找到合约地址[{}]对应的Erc Token", tx.getHash(), tokenLog.getAddress());
                }
            });
            tx.setErc20TxInfo(getErcTxInfo(tx.getErc20TxList()));
            tx.setErc721TxInfo(getErcTxInfo(tx.getErc721TxList()));
            log.info("当前交易[{}]有[{}]笔log,其中token交易有[{}]笔，其中erc20有[{}]笔,其中erc721有[{}]笔",
                    tx.getHash(),
                    CommonUtil.ofNullable(() -> receipt.getLogs().size()).orElse(0),
                    CommonUtil.ofNullable(() -> tokenLogs.size()).orElse(0),
                    CommonUtil.ofNullable(() -> tx.getErc20TxList().size()).orElse(0),
                    CommonUtil.ofNullable(() -> tx.getErc721TxList().size()).orElse(0));
        } catch (Exception e) {
            log.error(StrUtil.format("当前交易[{}]解析ERC交易异常", tx.getHash()), e);
        }
    }

}
