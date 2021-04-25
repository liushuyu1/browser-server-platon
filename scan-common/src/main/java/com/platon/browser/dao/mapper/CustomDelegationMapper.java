package com.platon.browser.dao.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.platon.browser.bean.CustomDelegation;
import com.platon.browser.bean.DelegationAddress;
import com.platon.browser.bean.DelegationStaking;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomDelegationMapper {

    List<CustomDelegation> selectByNodeId(@Param("nodeId") String nodeId);

    List<CustomDelegation> selectByNodeIdList(@Param("nodeIds") List<String> nodeIds);

    /**
     * @param page   分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param nodeId
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.DelegationStaking>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<DelegationStaking> selectStakingByNodeId(IPage<DelegationStaking> page, @Param("nodeId") String nodeId);

    /**
     * @param page         分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param delegateAddr
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.DelegationAddress>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<DelegationAddress> selectAddressByAddr(IPage<DelegationAddress> page, @Param("delegateAddr") String delegateAddr);

}
