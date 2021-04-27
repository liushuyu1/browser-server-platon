package com.platon.browser.dao.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.platon.browser.dao.entity.Node;
import com.platon.browser.dao.entity.NodeExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

public interface CustomNodeMapper {

    /**
     * 根据nodeId查询nodeName
     *
     * @param nodeId
     * @return
     * @method findNameById
     */
    String findNameById(String nodeId);

    /**
     * 根据条件查询列表
     *
     * @param page    分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param example
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.dao.entity.Node>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<Node> selectListByExample(IPage<Node> page, @Param("example") NodeExample example);


    int selectCountByActive();

    /**
     * 根据nodeIds查询nodeName
     *
     * @param nodeIds
     * @return java.util.List<java.lang.String>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/1
     */
    List<Node> batchFindNodeNameByNodeId(@Param("nodeIds") Set<String> nodeIds);

    /**
     * @param page      分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param status1
     * @param isSettle1
     * @param isUnion
     * @param status2
     * @param isSettle2
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.dao.entity.Node>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<Node> findAliveStakingList(IPage<Node> page, Integer status1, Integer isSettle1, boolean isUnion, Integer status2, Integer isSettle2);

}