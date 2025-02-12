package com.platon.browser.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.Page;
import com.platon.browser.bean.*;
import com.platon.browser.bean.http.CustomHttpClient;
import com.platon.browser.dao.custommapper.*;
import com.platon.browser.dao.entity.*;
import com.platon.browser.dao.mapper.*;
import com.platon.browser.elasticsearch.dto.ErcTx;
import com.platon.browser.enums.AddressTypeEnum;
import com.platon.browser.enums.ErcTypeEnum;
import com.platon.browser.service.erc.ErcServiceImpl;
import com.platon.browser.utils.AddressUtil;
import com.platon.browser.utils.AppStatusUtil;
import com.platon.browser.utils.TaskUtil;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * token定时任务
 *
 * @date: 2021/11/30
 */
@Slf4j
@Component
public class ErcTokenUpdateTask {

    /**
     * token_inventory表重试次数
     */
    @Value("${platon.token-retry-num:3}")
    private int tokenRetryNum;

    @Resource
    private TokenInventoryMapper token721InventoryMapper;

    @Resource
    private Token1155InventoryMapper token1155InventoryMapper;

    @Resource
    private CustomTokenInventoryMapper customToken721InventoryMapper;

    @Resource
    private CustomToken1155InventoryMapper customToken1155InventoryMapper;

    @Resource
    private CustomTokenHolderMapper customTokenHolderMapper;

    @Resource
    private CustomToken1155HolderMapper customToken1155HolderMapper;

    @Resource
    private CustomTokenMapper customTokenMapper;

    @Resource
    private TokenMapper tokenMapper;

    @Resource
    private CustomAddressMapper customAddressMapper;

    @Resource
    private ErcServiceImpl ercServiceImpl;

    @Resource
    private PointLogMapper pointLogMapper;

    @Resource
    private TxErc20BakMapper txErc20BakMapper;

    @Resource
    private TxErc721BakMapper txErc721BakMapper;

    @Resource
    private TxErc1155BakMapper txErc1155BakMapper;

    private static final int TOKEN_BATCH_SIZE = 10;

    private static final ExecutorService TOKEN_UPDATE_POOL = Executors.newFixedThreadPool(TOKEN_BATCH_SIZE);

    private static final int HOLDER_BATCH_SIZE = 10;

    private final Lock lock = new ReentrantLock();

    private final Lock tokenInventoryLock = new ReentrantLock();


    /**
     * 全量更新token的总供应量
     * 每5分钟更新
     *
     * @return void
     * @date 2021/1/18
     */
    @XxlJob("totalUpdateTokenTotalSupplyJobHandler")
    public void totalUpdateTokenTotalSupply() {
        lock.lock();
        try {
            updateTokenTotalSupply();
        } catch (Exception e) {
            log.warn("全量更新token的总供应量异常", e);
            throw e;
        } finally {
            lock.unlock();
        }
    }


    /**
     * 全量更新token库存信息
     * 每天凌晨1点执行一次
     *
     * @param
     * @return void
     * @date 2021/4/17
     */
    @XxlJob("totalUpdateTokenInventoryJobHandler")
    public void totalUpdateTokenInventory() {
        tokenInventoryLock.lock();
        try {
            updateToken721Inventory();
            updateToken1155Inventory();
        } catch (Exception e) {
            log.error("更新token库存信息", e);
        } finally {
            tokenInventoryLock.unlock();
        }
    }

    /**
     * 增量更新token库存信息
     * 每1分钟执行一次
     *
     * @param
     * @return void
     * @date 2021/2/1
     */
    @XxlJob("incrementUpdateTokenInventoryJobHandler")
    public void incrementUpdateTokenInventory() {
        if (tokenInventoryLock.tryLock()) {
            try {
                cronIncrementUpdateToken721Inventory();
                cronIncrementUpdateToken1155Inventory();
            } catch (Exception e) {
                log.warn("增量更新token库存信息异常", e);
            } finally {
                tokenInventoryLock.unlock();
            }
        } else {
            log.warn("该次token库存增量更新抢不到锁");
        }
    }

    /**
     * 销毁的721合约更新余额
     * 每10分钟执行一次
     *
     * @param :
     * @return: void
     * @date: 2021/9/27
     */
    @XxlJob("contractDestroyUpdateBalanceJobHandler")
    public void contractDestroyUpdateBalance() {
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        contractErc20DestroyUpdateBalance();
        contractErc721DestroyUpdateBalance();
        contractErc1155DestroyUpdateBalance();
    }

    /**
     * 更新ERC20和Erc721Enumeration token的总供应量===》全量更新
     *
     * @return void
     * @date 2021/1/18
     */
    private void updateTokenTotalSupply() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        Set<ErcToken> updateParams = new ConcurrentHashSet<>();
        List<List<ErcToken>> batchList = new ArrayList<>();
        List<ErcToken> batch = new ArrayList<>();
        batchList.add(batch);
        List<ErcToken> tokens = getErcTokens();
        for (ErcToken token : tokens) {
            if (token.isDirty()) {
                updateParams.add(token);
            }
            if (!(token.getTypeEnum() == ErcTypeEnum.ERC20 || token.getIsSupportErc721Enumeration())) {
                continue;
            }
            if (batch.size() == TOKEN_BATCH_SIZE) {
                // 本批次达到大小限制，则新建批次，并加入批次列表
                batch = new ArrayList<>();
                batchList.add(batch);
            }
            // 加入批次中
            batch.add(token);
        }
        // 分批并发查询Token totalSupply
        batchList.forEach(b -> {
            // 过滤销毁的合约
            List<ErcToken> res = tokenSubtractToList(b, getDestroyContracts());
            if (CollUtil.isNotEmpty(res)) {
                CountDownLatch latch = new CountDownLatch(res.size());
                for (ErcToken token : res) {
                    TOKEN_UPDATE_POOL.submit(() -> {
                        try {
                            // 查询总供应量
                            BigInteger totalSupply = ercServiceImpl.getTotalSupply(token.getAddress());
                            totalSupply = totalSupply == null ? BigInteger.ZERO : totalSupply;
                            if (ObjectUtil.isNull(token.getTotalSupply()) || !token.getTotalSupply().equalsIgnoreCase(totalSupply.toString())) {
                                TaskUtil.console("token[{}]的总供应量有变动需要更新旧值[{}]新值[{}]", token.getAddress(), token.getTotalSupply(), totalSupply);
                                // 有变动添加到更新列表中
                                token.setTotalSupply(totalSupply.toString());
                                updateParams.add(token);
                            }
                        } catch (Exception e) {
                            XxlJobHelper.log(StrUtil.format("该token[{}]查询总供应量异常", token.getAddress()));
                            log.error("查询总供应量异常", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            }
        });
        if (!updateParams.isEmpty()) {
            // 批量更新总供应量有变动的记录
            customTokenMapper.batchUpdateTokenTotalSupply(new ArrayList<>(updateParams));
            XxlJobHelper.handleSuccess("全量更新token的总供应量成功");
            updateParams.forEach(token -> token.setDirty(false));
        }
        XxlJobHelper.log("全量更新token的总供应量成功");
    }

    /**
     * 更新erc20的token holder的余额
     */
    @XxlJob("incrementUpdatePrc20TokenHolderBalanceJobHandler")
    public void incrementUpdateErc20TokenHolderBalance() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 50);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(5);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc20BakExample example = new TxErc20BakExample();
            example.setOrderByClause("id asc limit " + pageSize);
            example.createCriteria().andIdGreaterThan(oldPosition);
            List<TxErc20Bak> list = txErc20BakMapper.selectByExample(example);
            List<TokenHolder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc20]当前页数为[{}]，断点为[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc20]该断点[{}]未找到交易", oldPosition);
                return;
            }
            HashMap<String, HashSet<String>> map = new HashMap();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                if (map.containsKey(v.getContract())) {
                    // 判断是否是0地址
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        map.get(v.getContract()).add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        map.get(v.getContract()).add(v.getFrom());
                    }
                } else {
                    HashSet<String> addressSet = new HashSet<String>();
                    // 判断是否是0地址
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        addressSet.add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        addressSet.add(v.getFrom());
                    }
                    map.put(v.getContract(), addressSet);
                }
            });
            if (MapUtil.isNotEmpty(map)) {
                // 批量查询
                List<TokenHolder> tokenHolderKeyList = new ArrayList<>();
                map.forEach((contract, addressSet) -> {
                    addressSet.forEach(address -> {
                        TokenHolder holder = new TokenHolder();
                        holder.setTokenAddress(contract);
                        holder.setAddress(address);
                        tokenHolderKeyList.add(holder);
                    });
                });
                List<TokenHolder> tokenHolderList = ercServiceImpl.batchBalanceOfOwner(tokenHolderKeyList);
                updateParams.addAll(tokenHolderList.stream().filter(item -> item.getBalance() != null).collect(Collectors.toList()));
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customTokenHolderMapper.batchUpdate(updateParams);
                TaskUtil.console("更新[erc20] token holder的余额{}", JSONUtil.toJsonStr(updateParams));
                XxlJobHelper.handleSuccess("更新[erc20] token holder的余额成功");
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("更新[erc20] token holder的余额成功，断点为[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("更新token持有者余额异常", e);
        }
    }

    /**
     * 更新erc721的token holder的余额
     */
    @XxlJob("incrementUpdatePrc721TokenHolderBalanceJobHandler")
    public void incrementUpdateErc721TokenHolderBalance() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 50);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(6);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc721BakExample example = new TxErc721BakExample();
            example.setOrderByClause("id asc limit " + pageSize);
            example.createCriteria().andIdGreaterThan(oldPosition);
            List<TxErc721Bak> list = txErc721BakMapper.selectByExample(example);
            List<TokenHolder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc721]当前页数为[{}]，断点为[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc721]该断点[{}]未找到交易", oldPosition);
                return;
            }
            HashMap<String, HashSet<String>> map = new HashMap();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                if (map.containsKey(v.getContract())) {
                    // 判断是否是0地址
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        map.get(v.getContract()).add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        map.get(v.getContract()).add(v.getFrom());
                    }
                } else {
                    HashSet<String> addressSet = new HashSet<String>();
                    // 判断是否是0地址
                    if (!AddressUtil.isAddrZero(v.getTo())) {
                        addressSet.add(v.getTo());
                    }
                    if (!AddressUtil.isAddrZero(v.getFrom())) {
                        addressSet.add(v.getFrom());
                    }
                    map.put(v.getContract(), addressSet);
                }
            });
            if (MapUtil.isNotEmpty(map)) {
                // 批量查询
                List<TokenHolder> tokenHolderKeyList = new ArrayList<>();
                map.forEach((contract, addressSet) -> {
                    addressSet.forEach(address -> {
                        TokenHolder holder = new TokenHolder();
                        holder.setTokenAddress(contract);
                        holder.setAddress(address);
                        tokenHolderKeyList.add(holder);
                    });
                });
                List<TokenHolder> tokenHolderList = ercServiceImpl.batchBalanceOfOwner(tokenHolderKeyList);
                updateParams.addAll(tokenHolderList.stream().filter(item -> item.getBalance() != null).collect(Collectors.toList()));
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customTokenHolderMapper.batchUpdate(updateParams);
                TaskUtil.console("更新[erc721] token holder的余额{}", JSONUtil.toJsonStr(updateParams));
                XxlJobHelper.handleSuccess("更新[erc721] token holder的余额成功");
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("更新[erc721] token holder的余额成功，断点为[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("更新token持有者余额异常", e);
        }
    }

    /**
     * 更新erc1155的token holder的余额
     */
    @XxlJob("incrementUpdatePrc1155TokenHolderBalanceJobHandler")
    public void incrementUpdateErc1155TokenHolderBalance() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        try {
            int pageSize = Convert.toInt(XxlJobHelper.getJobParam(), 30);
            PointLog pointLog = pointLogMapper.selectByPrimaryKey(12);
            long oldPosition = Convert.toLong(pointLog.getPosition());
            TxErc1155BakExample example = new TxErc1155BakExample();
            example.setOrderByClause("id asc limit " + pageSize);
            example.createCriteria().andIdGreaterThan(oldPosition);
            List<TxErc1155Bak> list = txErc1155BakMapper.selectByExample(example);
            List<Token1155Holder> updateParams = new ArrayList<>();
            TaskUtil.console("[erc1155]当前页数为[{}]，断点为[{}]", pageSize, oldPosition);
            if (CollUtil.isEmpty(list)) {
                TaskUtil.console("[erc1155]该断点[{}]未找到交易", oldPosition);
                return;
            }
            Map<String, Map<String, HashSet<String>>> contract2tokenId2addressSet = new HashMap<>();
            list.sort(Comparator.comparing(ErcTx::getSeq));
            list.forEach(v -> {
                Map<String, HashSet<String>> tokenId2addressSet = contract2tokenId2addressSet.computeIfAbsent(v.getContract(), contract -> new HashMap<>());
                HashSet<String> addressSet = tokenId2addressSet.computeIfAbsent(v.getTokenId(), tokenId -> new HashSet<>());
                if (!AddressUtil.isAddrZero(v.getTo())) {
                    addressSet.add(v.getTo());
                }
                if (!AddressUtil.isAddrZero(v.getFrom())) {
                    addressSet.add(v.getFrom());
                }
            });
            if (MapUtil.isNotEmpty(contract2tokenId2addressSet)) {
                // 批量查询
                List<Token1155Holder> tokenHolderKeyList = new ArrayList<>();
                for (String contract: contract2tokenId2addressSet.keySet()) {
                    for (String tokenId:contract2tokenId2addressSet.get(contract).keySet()){
                        for (String address : contract2tokenId2addressSet.get(contract).get(tokenId)) {
                            Token1155Holder holder = new Token1155Holder();
                            holder.setTokenAddress(contract);
                            holder.setTokenId(tokenId);
                            holder.setAddress(address);
                            tokenHolderKeyList.add(holder);
                        }
                    }
                }
                List<Token1155Holder> tokenHolderList = ercServiceImpl.batchBalanceOfOwnerAndId(tokenHolderKeyList);
                updateParams.addAll(tokenHolderList.stream().filter(item -> item.getBalance() != null).collect(Collectors.toList()));
            }
            if (CollUtil.isNotEmpty(updateParams)) {
                customToken1155HolderMapper.batchUpdate(updateParams);
                TaskUtil.console("更新[erc1155] token holder的余额{}", JSONUtil.toJsonStr(updateParams));
                XxlJobHelper.handleSuccess("更新[erc1155] token holder的余额成功");
            }
            String newPosition = CollUtil.getLast(list).getId().toString();
            pointLog.setPosition(newPosition);
            pointLogMapper.updateByPrimaryKeySelective(pointLog);
            XxlJobHelper.log("更新[erc1155] token holder的余额成功，断点为[{}]->[{}]", oldPosition, newPosition);
        } catch (Exception e) {
            log.error("更新1155token持有者余额异常", e);
        }
    }

    /**
     * 更新token库存信息
     *
     * @param :
     * @return: void
     * @date: 2022/9/21
     */
    private void updateToken721Inventory() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        long id = customToken721InventoryMapper.findMaxId() + 1;
        // 分页更新token库存相关信息
        List<TokenInventoryWithBLOBs> batch = null;
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        do {
            // 当前失败的条数
            AtomicInteger errorNum = new AtomicInteger(0);
            // 当次更新的条数
            AtomicInteger updateNum = new AtomicInteger(0);
            try {
                TokenInventoryExample condition = new TokenInventoryExample();
                condition.setOrderByClause(" id desc limit " + batchSize);
                condition.createCriteria().andRetryNumLessThan(tokenRetryNum).andImageIsNull().andIdLessThan(id);
                batch = token721InventoryMapper.selectByExampleWithBLOBs(condition);
                List<TokenInventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(batch)) {
                    batch.forEach(inventory -> {
                        TokenInventoryWithBLOBs updateTokenInventory = new TokenInventoryWithBLOBs();
                        updateTokenInventory.setTokenId(inventory.getTokenId());
                        updateTokenInventory.setTokenAddress(inventory.getTokenAddress());
                        updateTokenInventory.setTokenUrl(inventory.getTokenUrl());
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    UpdateTokenInventory newTi = JSONUtil.toBean(resp, UpdateTokenInventory.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // 只要有一个属性变动就添加到更新列表中
                                    if (ObjectUtil.isNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImageUrl())) {
                                        updateTokenInventory.setImage(newTi.getImageUrl());
                                        changed = true;
                                    } else if (ObjectUtil.isNotNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImageUrl()) && !inventory.getImage().equals(newTi.getImageUrl())) {
                                        updateTokenInventory.setImage(newTi.getImageUrl());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImage())) {
                                        updateTokenInventory.setImage(newTi.getImage());
                                        changed = true;
                                    } else if (ObjectUtil.isNotNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImage()) && !inventory.getImage().equals(newTi.getImage())) {
                                        updateTokenInventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) && ObjectUtil.isNotNull(newTi.getDescription())) {
                                        updateTokenInventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    } else if (ObjectUtil.isNotNull(inventory.getDescription()) && ObjectUtil.isNotNull(newTi.getDescription()) && !inventory.getDescription()
                                                                                                                                                             .equals(newTi.getDescription())) {
                                        updateTokenInventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) && ObjectUtil.isNotNull(newTi.getName())) {
                                        updateTokenInventory.setName(newTi.getName());
                                        changed = true;
                                    } else if (ObjectUtil.isNotNull(inventory.getName()) && ObjectUtil.isNotNull(newTi.getName()) && !inventory.getName().equals(newTi.getName())) {
                                        updateTokenInventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        updateTokenInventory.setRetryNum(0);
                                        updateParams.add(updateTokenInventory);
                                        log.info("库存有属性变动需要更新,token[{}]", JSONUtil.toJsonStr(updateTokenInventory));
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(updateTokenInventory);
                                    log.warn("http请求异常：http状态码:{},http消息:{},token:{}", response.code(), response.message(), JSONUtil.toJsonStr(updateTokenInventory));
                                }
                            } else {
                                errorNum.getAndIncrement();
                                updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(updateTokenInventory);
                                String msg = StrUtil.format("请求TokenURI为空,,token:{}", JSONUtil.toJsonStr(updateTokenInventory));
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(updateTokenInventory);
                            log.warn(StrUtil.format("全量更新token库存信息异常,token:{}", JSONUtil.toJsonStr(updateTokenInventory)), e);
                        }
                    });
                    id = batch.get(batch.size() - 1).getId();
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken721InventoryMapper.batchUpdateTokenInfo(updateParams);
                    XxlJobHelper.log("全量更新token库存信息{}", JSONUtil.toJsonStr(updateParams));
                }
                String msg = StrUtil.format("全量更新token库存信息:查询到的条数为{},已更新的条数为:{},失败的条数为:{}", batch.size(), updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } catch (Exception e) {
                log.error(StrUtil.format("全量更新token库存信息异常"), e);
            }
        } while (CollUtil.isNotEmpty(batch));
    }

    /**
     * 更新token库存信息
     *
     * @param :
     * @return: void
     * @date: 2022/9/21
     */
    private void updateToken1155Inventory() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // 分页更新token库存相关信息
        List<Token1155InventoryWithBLOBs> batch = null;
        long id = customToken1155InventoryMapper.findMaxId() + 1;
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        do {
            // 当前查询到的条数
            int batchNum = 0;
            // 当前失败的条数
            AtomicInteger errorNum = new AtomicInteger(0);
            // 当次更新的条数
            AtomicInteger updateNum = new AtomicInteger(0);
            try {
                Token1155InventoryExample condition = new Token1155InventoryExample();
                condition.setOrderByClause(" id desc limit " + batchSize);
                condition.createCriteria().andRetryNumLessThan(tokenRetryNum).andImageIsNull().andIdLessThan(id);
                batch = token1155InventoryMapper.selectByExampleWithBLOBs(condition);
                // 过滤销毁的合约
                List<Token1155InventoryWithBLOBs> res = token1155InventorySubtractToList(batch, getDestroyContracts());
                List<Token1155InventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    Token1155InventoryWithBLOBs newTi = JSONUtil.toBean(resp, Token1155InventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // 只要有一个属性变动就添加到更新列表中
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDecimal()) || !newTi.getDecimal().equals(inventory.getDecimal())) {
                                        inventory.setDecimal(newTi.getDecimal());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        log.info("1155token[{}]库存有属性变动需要更新,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}],ecimal[{}]",
                                                 inventory.getTokenAddress(),
                                                 inventory.getTokenUrl(),
                                                 inventory.getName(),
                                                 inventory.getDescription(),
                                                 inventory.getImage(),
                                                 inventory.getDecimal());
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    log.warn("http请求异常：http状态码:{},http消息:{},1155token_address:{},token_id:{},tokenURI:{},重试次数:{}",
                                             response.code(),
                                             response.message(),
                                             inventory.getTokenAddress(),
                                             inventory.getTokenId(),
                                             inventory.getTokenUrl(),
                                             inventory.getRetryNum());
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("请求TokenURI为空,1155token_address：{},token_id:{},重试次数:{}", inventory.getTokenAddress(), inventory.getTokenId(), inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("全量更新1155token库存信息异常,token_address：{},token_id:{},tokenURI:{},重试次数:{}",
                                                    inventory.getTokenAddress(),
                                                    inventory.getTokenId(),
                                                    inventory.getTokenUrl(),
                                                    inventory.getRetryNum()), e);
                        }
                    });
                    id = batch.get(batch.size() - 1).getId();
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken1155InventoryMapper.batchInsertOrUpdateSelective(updateParams, Token1155Inventory.Column.values());
                    XxlJobHelper.log("全量更新1155token库存信息{}", JSONUtil.toJsonStr(updateParams));
                }
                String msg = StrUtil.format("全量更新1155token库存信息:查询到的条数为{},过滤后的条数:{},已更新的条数为:{},失败的条数为:{}", batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } catch (Exception e) {
                log.error(StrUtil.format("全量更新1155token库存信息异常,当前标识为"), e);
            }
        } while (CollUtil.isNotEmpty(batch));
    }

    /**
     * 更新token库存信息=>增量更新
     *
     * @return void
     * @date 2021/4/26
     */
    private void cronIncrementUpdateToken721Inventory() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // 当前失败的条数
        AtomicInteger errorNum = new AtomicInteger(0);
        // 当次更新的条数
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(7);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 10);
        XxlJobHelper.log("当前页数为[{}]，断点为[{}]", batchSize, oldPosition);
        try {
            TokenInventoryExample condition = new TokenInventoryExample();
            condition.setOrderByClause(" id asc limit " + batchSize);
            condition.createCriteria().andIdGreaterThan(oldPosition).andRetryNumLessThan(tokenRetryNum).andImageIsNull();
            List<TokenInventoryWithBLOBs> batch = token721InventoryMapper.selectByExampleWithBLOBs(condition);
            if (CollUtil.isNotEmpty(batch)) {
                List<TokenInventoryWithBLOBs> updateParams = new ArrayList<>();
                batch.forEach(inventory -> {
                    TokenInventoryWithBLOBs updateTokenInventory = new TokenInventoryWithBLOBs();
                    updateTokenInventory.setTokenId(inventory.getTokenId());
                    updateTokenInventory.setTokenAddress(inventory.getTokenAddress());
                    updateTokenInventory.setTokenUrl(inventory.getTokenUrl());
                    try {
                        if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                            Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                            Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                            if (response.code() == 200) {
                                String resp = response.body().string();
                                UpdateTokenInventory newTi = JSONUtil.toBean(resp, UpdateTokenInventory.class);
                                newTi.setTokenId(inventory.getTokenId());
                                newTi.setTokenAddress(inventory.getTokenAddress());
                                boolean changed = false;
                                // 只要有一个属性变动就添加到更新列表中
                                if (ObjectUtil.isNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImageUrl())) {
                                    updateTokenInventory.setImage(newTi.getImageUrl());
                                    changed = true;
                                } else if (ObjectUtil.isNotNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImageUrl()) && !inventory.getImage().equals(newTi.getImageUrl())) {
                                    updateTokenInventory.setImage(newTi.getImageUrl());
                                    changed = true;
                                }
                                if (ObjectUtil.isNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImage())) {
                                    updateTokenInventory.setImage(newTi.getImage());
                                    changed = true;
                                } else if (ObjectUtil.isNotNull(inventory.getImage()) && ObjectUtil.isNotNull(newTi.getImage()) && !inventory.getImage().equals(newTi.getImage())) {
                                    updateTokenInventory.setImage(newTi.getImage());
                                    changed = true;
                                }
                                if (ObjectUtil.isNull(inventory.getDescription()) && ObjectUtil.isNotNull(newTi.getDescription())) {
                                    updateTokenInventory.setDescription(newTi.getDescription());
                                    changed = true;
                                } else if (ObjectUtil.isNotNull(inventory.getDescription()) && ObjectUtil.isNotNull(newTi.getDescription()) && !inventory.getDescription()
                                                                                                                                                         .equals(newTi.getDescription())) {
                                    updateTokenInventory.setDescription(newTi.getDescription());
                                    changed = true;
                                }
                                if (ObjectUtil.isNull(inventory.getName()) && ObjectUtil.isNotNull(newTi.getName())) {
                                    updateTokenInventory.setName(newTi.getName());
                                    changed = true;
                                } else if (ObjectUtil.isNotNull(inventory.getName()) && ObjectUtil.isNotNull(newTi.getName()) && !inventory.getName().equals(newTi.getName())) {
                                    updateTokenInventory.setName(newTi.getName());
                                    changed = true;
                                }
                                if (changed) {
                                    updateNum.getAndIncrement();
                                    updateTokenInventory.setRetryNum(0);
                                    updateParams.add(updateTokenInventory);
                                    String msg = StrUtil.format("库存有属性变动需要更新,token[{}]", JSONUtil.toJsonStr(updateTokenInventory));
                                    XxlJobHelper.log(msg);
                                    log.info(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(updateTokenInventory);
                                String msg = StrUtil.format("http请求异常：http状态码:{},http消息:{},断点:{},token:{}", response.code(), response.message(), oldPosition, JSONUtil.toJsonStr(updateTokenInventory));
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } else {
                            errorNum.getAndIncrement();
                            updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(updateTokenInventory);
                            String msg = StrUtil.format("请求TokenURI为空,断点:{},token：{}", oldPosition, JSONUtil.toJsonStr(updateTokenInventory));
                            XxlJobHelper.log(msg);
                            log.warn(msg);
                        }
                    } catch (Exception e) {
                        errorNum.getAndIncrement();
                        updateTokenInventory.setRetryNum(inventory.getRetryNum() + 1);
                        updateParams.add(updateTokenInventory);
                        log.warn(StrUtil.format("增量更新token库存信息异常,断点:{},token：{}", oldPosition, JSONUtil.toJsonStr(updateTokenInventory)), e);
                    }
                });
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken721InventoryMapper.batchUpdateTokenInfo(updateParams);
                }
                TokenInventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("增量更新token库存信息:断点为[{}]->[{}],查询到的条数为:{},已更新的条数为:{},失败的条数为:{}", oldPosition, newPosition, batch.size(), updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
                XxlJobHelper.handleSuccess(msg);
            } else {
                XxlJobHelper.log("增量更新token库存信息完成，未找到数据，断点为[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("增量更新token库存信息异常,断点:{}", oldPosition), e);
        }
    }

    /**
     * 更新token1155库存信息=>增量更新
     *
     * @return void
     * @date 2022/2/14
     */
    /**
     * 更新token库存信息=>增量更新
     *
     * @return void
     * @date 2022/2/14
     */
    private void cronIncrementUpdateToken1155Inventory() {
        // 只有程序正常运行才执行任务
        if (!AppStatusUtil.isRunning()) {
            return;
        }
        // 当前查询到的条数
        int batchNum = 0;
        // 当前失败的条数
        AtomicInteger errorNum = new AtomicInteger(0);
        // 当次更新的条数
        AtomicInteger updateNum = new AtomicInteger(0);
        PointLog pointLog = pointLogMapper.selectByPrimaryKey(9);
        Long oldPosition = Convert.toLong(pointLog.getPosition());
        int batchSize = Convert.toInt(XxlJobHelper.getJobParam(), 100);
        XxlJobHelper.log("当前页数为[{}]，断点为[{}]", batchSize, oldPosition);
        try {
            Token1155InventoryExample condition = new Token1155InventoryExample();
            condition.setOrderByClause("id");
            condition.createCriteria().andIdGreaterThan(oldPosition).andIdLessThanOrEqualTo(oldPosition + batchSize).andRetryNumLessThan(tokenRetryNum);
            // 分页更新token库存相关信息
            List<Token1155InventoryWithBLOBs> batch = token1155InventoryMapper.selectByExampleWithBLOBs(condition);
            if (CollUtil.isNotEmpty(batch)) {
                List<Token1155InventoryWithBLOBs> res = token1155InventorySubtractToList(batch, getDestroyContracts());
                List<Token1155InventoryWithBLOBs> updateParams = new ArrayList<>();
                if (CollUtil.isNotEmpty(res)) {
                    batchNum = res.size();
                    res.forEach(inventory -> {
                        try {
                            if (StrUtil.isNotBlank(inventory.getTokenUrl())) {
                                Request request = new Request.Builder().url(inventory.getTokenUrl()).build();
                                Response response = CustomHttpClient.getOkHttpClient().newCall(request).execute();
                                if (response.code() == 200) {
                                    String resp = response.body().string();
                                    Token1155InventoryWithBLOBs newTi = JSONUtil.toBean(resp, Token1155InventoryWithBLOBs.class);
                                    newTi.setTokenId(inventory.getTokenId());
                                    newTi.setTokenAddress(inventory.getTokenAddress());
                                    boolean changed = false;
                                    // 只要有一个属性变动就添加到更新列表中
                                    if (ObjectUtil.isNull(inventory.getImage()) || !newTi.getImage().equals(inventory.getImage())) {
                                        inventory.setImage(newTi.getImage());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDescription()) || !newTi.getDescription().equals(inventory.getDescription())) {
                                        inventory.setDescription(newTi.getDescription());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getName()) || !newTi.getName().equals(inventory.getName())) {
                                        inventory.setName(newTi.getName());
                                        changed = true;
                                    }
                                    if (ObjectUtil.isNull(inventory.getDecimal()) || !newTi.getDecimal().equals(inventory.getDecimal())) {
                                        inventory.setDecimal(newTi.getDecimal());
                                        changed = true;
                                    }
                                    if (changed) {
                                        updateNum.getAndIncrement();
                                        inventory.setRetryNum(0);
                                        updateParams.add(inventory);
                                        String msg = StrUtil.format("token[{}]库存有属性变动需要更新,tokenURL[{}],tokenName[{}],tokenDesc[{}],tokenImage[{}],decimal[{}]",
                                                                    inventory.getTokenAddress(),
                                                                    inventory.getTokenUrl(),
                                                                    inventory.getName(),
                                                                    inventory.getDescription(),
                                                                    inventory.getImage(),
                                                                    inventory.getDecimal());
                                        XxlJobHelper.log(msg);
                                        log.info(msg);
                                    }
                                } else {
                                    errorNum.getAndIncrement();
                                    inventory.setRetryNum(inventory.getRetryNum() + 1);
                                    updateParams.add(inventory);
                                    String msg = StrUtil.format("http请求异常：http状态码:{},http消息:{},断点:{},token_address:{},token_id:{},tokenURI:{},重试次数:{}",
                                                                response.code(),
                                                                response.message(),
                                                                oldPosition,
                                                                inventory.getTokenAddress(),
                                                                inventory.getTokenId(),
                                                                inventory.getTokenUrl(),
                                                                inventory.getRetryNum());
                                    XxlJobHelper.log(msg);
                                    log.warn(msg);
                                }
                            } else {
                                errorNum.getAndIncrement();
                                inventory.setRetryNum(inventory.getRetryNum() + 1);
                                updateParams.add(inventory);
                                String msg = StrUtil.format("请求TokenURI为空,断点:{},token_address：{},token_id:{},重试次数:{}",
                                                            oldPosition,
                                                            inventory.getTokenAddress(),
                                                            inventory.getTokenId(),
                                                            inventory.getRetryNum());
                                XxlJobHelper.log(msg);
                                log.warn(msg);
                            }
                        } catch (Exception e) {
                            errorNum.getAndIncrement();
                            inventory.setRetryNum(inventory.getRetryNum() + 1);
                            updateParams.add(inventory);
                            log.warn(StrUtil.format("增量更新token库存信息异常,断点:{},token_address：{},token_id:{},tokenURI:{},重试次数:{}",
                                                    oldPosition,
                                                    inventory.getTokenAddress(),
                                                    inventory.getTokenId(),
                                                    inventory.getTokenUrl(),
                                                    inventory.getRetryNum()), e);
                        }
                    });
                }
                if (CollUtil.isNotEmpty(updateParams)) {
                    customToken1155InventoryMapper.batchInsertOrUpdateSelective(updateParams, Token1155Inventory.Column.values());
                }
                Token1155Inventory lastTokenInventory = CollUtil.getLast(batch);
                String newPosition = Convert.toStr(lastTokenInventory.getId());
                pointLog.setPosition(newPosition);
                pointLogMapper.updateByPrimaryKeySelective(pointLog);
                String msg = StrUtil.format("增量更新token库存信息:断点为[{}]->[{}],查询到的条数为:{},过滤后的条数:{},已更新的条数为:{},失败的条数为:{}", oldPosition, newPosition, batch.size(), batchNum, updateNum.get(), errorNum.get());
                XxlJobHelper.log(msg);
                log.info(msg);
            } else {
                XxlJobHelper.log("增量更新token库存信息完成，未找到数据，断点为[{}]", oldPosition);
            }
        } catch (Exception e) {
            log.error(StrUtil.format("增量更新token库存信息异常,断点:{}", oldPosition), e);
        }
    }

    private void contractErc20DestroyUpdateBalance() {
        try {
            List<DestroyContract> tokenList = customTokenMapper.findDestroyContract(ErcTypeEnum.ERC20.getDesc());
            if (CollUtil.isNotEmpty(tokenList)) {
                List<TokenHolder> updateList = new ArrayList<>();
                for (DestroyContract destroyContract : tokenList) {
                    try {
                        BigInteger balance = ercServiceImpl.getErc20HistoryBalance(destroyContract.getTokenAddress(),
                                                                                   destroyContract.getAccount(),
                                                                                   BigInteger.valueOf(destroyContract.getContractDestroyBlock() - 1));
                        TokenHolder tokenHolder = new TokenHolder();
                        tokenHolder.setTokenAddress(destroyContract.getTokenAddress());
                        tokenHolder.setAddress(destroyContract.getAccount());
                        tokenHolder.setBalance(balance.toString());
                        updateList.add(tokenHolder);
                    } catch (Exception e) {
                        log.error(StrUtil.format("已销毁的erc20合约[{}]账号[{}]更新余额异常", destroyContract.getTokenAddress(), destroyContract.getAccount()), e);
                    }
                }
                if (CollUtil.isNotEmpty(updateList)) {
                    customTokenHolderMapper.batchUpdate(updateList);
                    Set<String> destroyContractSet = updateList.stream().map(TokenHolderKey::getTokenAddress).collect(Collectors.toSet());
                    for (String destroyContract : destroyContractSet) {
                        Token token = new Token();
                        token.setAddress(destroyContract);
                        token.setContractDestroyUpdate(true);
                        tokenMapper.updateByPrimaryKeySelective(token);
                    }
                }
            }
        } catch (Exception e) {
            log.error("更新已销毁的erc20合约余额异常", e);
        }
    }

    /**
     * 过滤销毁的合约
     *
     * @param list:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.dao.entity.TokenInventory>
     * @date: 2022/2/14
     */
    private List<Token1155InventoryWithBLOBs> token1155InventorySubtractToList(List<Token1155InventoryWithBLOBs> list, Set<String> destroyContracts) {
        List<Token1155InventoryWithBLOBs> res = CollUtil.newArrayList();
        if (CollUtil.isNotEmpty(list)) {
            for (Token1155InventoryWithBLOBs tokenInventory : list) {
                if (!destroyContracts.contains(tokenInventory.getTokenAddress())) {
                    res.add(tokenInventory);
                }
            }
        }
        return res;
    }

    /**
     * 销毁的erc721更新余额
     *
     * @param :
     * @return: void
     * @date: 2021/9/27
     */
    private void contractErc721DestroyUpdateBalance() {
        try {
            //查询出被销毁的721合约地址
            List<String> contractErc721Destroys = customAddressMapper.findContractDestroy(AddressTypeEnum.ERC721_EVM_CONTRACT.getCode());
            if (CollUtil.isNotEmpty(contractErc721Destroys)) {
                for (String tokenAddress : contractErc721Destroys) {
                    //查询每个销毁721合约，所有持有人以及他们持有tokenID的数量
                    List<Erc721ContractDestroyBalanceVO> list = customToken721InventoryMapper.findErc721ContractDestroyBalance(tokenAddress);

                    //查询每个销毁721合约的所有持有人的记录
                    Page<CustomTokenHolder> ids = customTokenHolderMapper.selectERC721Holder(tokenAddress);
                    List<TokenHolder> updateParams = new ArrayList<>();
                    StringBuilder res = new StringBuilder();
                    //遍历每个持有人
                    for (CustomTokenHolder tokenHolder : ids) {

                        //过滤找出每个持有人的持有tokenID的数量
                        List<Erc721ContractDestroyBalanceVO> filterList = list.stream().filter(v -> v.getOwner().equalsIgnoreCase(tokenHolder.getAddress())).collect(Collectors.toList());
                        int balance = 0;
                        if (CollUtil.isNotEmpty(filterList)) {
                            //设置持有销毁721合约的余额（即持有token_id数量)
                            balance = filterList.get(0).getNum();
                        }
                        //如果持有人的持有销毁721合约的当前余额，和持有数量不一致，则更新
                        if (!tokenHolder.getBalance().equalsIgnoreCase(cn.hutool.core.convert.Convert.toStr(balance))) {
                            TokenHolder updateTokenHolder = new TokenHolder();
                            updateTokenHolder.setTokenAddress(tokenHolder.getTokenAddress());
                            updateTokenHolder.setAddress(tokenHolder.getAddress());
                            updateTokenHolder.setBalance(cn.hutool.core.convert.Convert.toStr(balance));
                            updateParams.add(updateTokenHolder);
                            res.append(StrUtil.format("[合约{}，余额{}->{}] ", tokenHolder.getAddress(), tokenHolder.getBalance(), cn.hutool.core.convert.Convert.toStr(balance)));
                        }
                    }
                    if (CollUtil.isNotEmpty(updateParams)) {
                        customTokenHolderMapper.batchUpdate(updateParams);
                        log.info("销毁的erc721[{}]更新余额成功，结果为{}", tokenAddress, res.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("销毁的erc721更新余额异常", e);
        }
    }

    /**
     * 销毁的erc1155更新余额
     *
     * @param :
     * @return: void
     * @date: 2022/2/14
     */
    private void contractErc1155DestroyUpdateBalance() {
        try {
            List<Erc1155ContractDestroyBean> contractErc1155Destroys = customToken1155HolderMapper.findDestroyContract(AddressTypeEnum.ERC1155_EVM_CONTRACT.getCode());
            if (CollUtil.isNotEmpty(contractErc1155Destroys)) {
                List<Token1155Holder> updateList = new ArrayList<>();
                for (Erc1155ContractDestroyBean erc1155ContractDestroyBean : contractErc1155Destroys) {
                    try {
                        BigInteger balance = ercServiceImpl.getErc1155HistoryBalance(erc1155ContractDestroyBean.getTokenAddress(),
                                                                                     new BigInteger(erc1155ContractDestroyBean.getTokenId()),
                                                                                     erc1155ContractDestroyBean.getAddress(),
                                                                                     BigInteger.valueOf(erc1155ContractDestroyBean.getContractDestroyBlock() - 1));
                        Token1155Holder tokenHolder = new Token1155Holder();
                        tokenHolder.setTokenAddress(erc1155ContractDestroyBean.getTokenAddress());
                        tokenHolder.setTokenId(erc1155ContractDestroyBean.getTokenId());
                        tokenHolder.setAddress(erc1155ContractDestroyBean.getAddress());
                        tokenHolder.setBalance(balance.toString());
                    } catch (Exception e) {
                        log.error(StrUtil.format("已销毁的erc1155合约[{}][{}]账号[{}]更新余额异常",
                                                 erc1155ContractDestroyBean.getTokenAddress(),
                                                 erc1155ContractDestroyBean.getTokenId(),
                                                 erc1155ContractDestroyBean.getAddress()), e);
                    }
                }
                if (CollUtil.isNotEmpty(updateList)) {
                    customToken1155HolderMapper.batchUpdate(updateList);
                    Set<String> destroyContractSet = updateList.stream().map(Token1155Holder::getTokenAddress).collect(Collectors.toSet());
                    for (String destroyContract : destroyContractSet) {
                        Token token = new Token();
                        token.setAddress(destroyContract);
                        token.setContractDestroyUpdate(true);
                        tokenMapper.updateByPrimaryKeySelective(token);
                    }
                }
            }
        } catch (Exception e) {
            log.error("销毁的erc1155更新余额异常", e);
        }
    }

    /**
     * 获取ercToken
     *
     * @param :
     * @return: java.util.List<com.platon.browser.v0152.bean.ErcToken>
     * @date: 2021/11/30
     */
    private List<ErcToken> getErcTokens() {
        List<ErcToken> ercTokens = new ArrayList<>();
        List<Token> tokens = tokenMapper.selectByExample(null);
        tokens.forEach(token -> {
            ErcToken et = new ErcToken();
            BeanUtils.copyProperties(token, et);
            ErcTypeEnum typeEnum = ErcTypeEnum.valueOf(token.getType().toUpperCase());
            et.setTypeEnum(typeEnum);
            ercTokens.add(et);
        });
        return ercTokens;
    }

    /**
     * 获取销毁的合约
     *
     * @param :
     * @return: java.util.Set<java.lang.String>
     * @date: 2021/11/30
     */
    private Set<String> getDestroyContracts() {
        Set<String> destroyContracts = new HashSet<>();
        List<String> list = customAddressMapper.findContractDestroy(null);
        destroyContracts.addAll(list);
        return destroyContracts;
    }

    /**
     * 过滤销毁的合约
     *
     * @param ercTokens:
     * @param destroyContracts:
     * @return: java.util.List<com.platon.browser.v0152.bean.ErcToken>
     * @date: 2021/10/14
     */
    private List<ErcToken> tokenSubtractToList(List<ErcToken> ercTokens, Set<String> destroyContracts) {
        List<ErcToken> res = CollUtil.newArrayList();
        for (ErcToken ercToken : ercTokens) {
            if (!destroyContracts.contains(ercToken.getAddress())) {
                res.add(ercToken);
            }
        }
        return res;
    }

    /**
     * 更新token对应的持有人的数量
     * todo: 在涉及的表记录数量多时，这个统计效率并不高。最好的做法是在查看token的持有人数量时，再实时统计。就是说，这个数据没有必要持续统计并更新到表中。
     * @param
     * @return void
     * @date 2021/3/17
     */

    @XxlJob("updateTokenHolderCountJobHandler")
    public void updateTokenHolderCount() {
        log.debug("开始执行:统计token的持有人数量任务");
        StopWatch watch = new StopWatch();
        watch.start("统计token的持有人数量");
        updateTokenHolderCount(ErcTypeEnum.ERC1155);
        // ERC20 AND ERC721
        updateTokenHolderCount(ErcTypeEnum.ERC20);
        watch.stop();
        log.debug("结束执行:统计token的持有人数量任务，耗时统计:{}ms", watch.getLastTaskTimeMillis());
        XxlJobHelper.log("统计token的持有人数量完成");
    }

    private void updateTokenHolderCount(ErcTypeEnum type){
        try {
            List<Token> tokenList;
            if (type == ErcTypeEnum.ERC1155){
                tokenList = customTokenMapper.count1155TokenHolder();
            } else {
                tokenList = customTokenMapper.countTokenHolder();
            }

            if (tokenList.size() > 0){
                customTokenMapper.batchUpdateTokenHolder(tokenList);
            }
        } catch (Exception e){
            log.error("统计token的持有人数量任务异常，type = {}", type, e);
        }
    }
}
