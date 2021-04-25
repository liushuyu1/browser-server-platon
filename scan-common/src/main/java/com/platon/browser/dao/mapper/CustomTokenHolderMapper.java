package com.platon.browser.dao.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platon.browser.bean.CustomTokenHolder;
import com.platon.browser.bean.TokenHolderCount;
import com.platon.browser.dao.entity.TokenHolder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomTokenHolderMapper {

    /**
     * @param page         分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param tokenAddress
     * @param address
     * @param type
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.CustomTokenHolder>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<CustomTokenHolder> selectListByParams(IPage<CustomTokenHolder> page, @Param("tokenAddress") String tokenAddress, @Param("address") String address, @Param("type") String type);

    /**
     * 查询erc721令牌数量
     *
     * @param page         分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param tokenAddress
     * @param address
     * @param type
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.CustomTokenHolder>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<CustomTokenHolder> findErc721TokenHolder(IPage<CustomTokenHolder> page, @Param("tokenAddress") String tokenAddress, @Param("address") String address, @Param("type") String type);

    int batchInsertOrUpdateSelective(@Param("list") List<TokenHolder> list, @Param("selective") TokenHolder.Column... selective);

    /**
     * 批量更新token持有者余额
     *
     * @param list
     * @return int
     * @author huangyongpeng@matrixelements.com
     * @date 2021/3/18
     */
    int batchUpdate(@Param("list") List<TokenHolder> list);

    /**
     * 查询token对应的持有人的数量
     *
     * @param
     * @return java.util.List<com.platon.browser.bean.TokenHolderCount>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/3/17
     */
    List<TokenHolderCount> findTokenHolderCount();

    /**
     * 查询erc721的TokenHolderList
     *
     * @param page         分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param tokenAddress
     * @param address
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.CustomTokenHolder>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<CustomTokenHolder> selectListByERC721(IPage<CustomTokenHolder> page, @Param("tokenAddress") String tokenAddress, @Param("address") String address);

    /**
     * 取余额为0的token holder
     *
     * @param type
     * @return
     */
    List<TokenHolder> getZeroBalanceTokenHolderList(
            @Param("type") String type,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("orderby") String orderby
    );

}