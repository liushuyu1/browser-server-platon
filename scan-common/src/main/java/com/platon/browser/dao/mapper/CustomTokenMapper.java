package com.platon.browser.dao.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platon.browser.bean.CustomToken;
import com.platon.browser.bean.CustomTokenDetail;
import com.platon.browser.dao.entity.Token;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CustomTokenMapper {

    /**
     * @param page 分页对象不能为空,入参IPage和返回结果IPage是同一个对象
     * @param type
     * @return com.baomidou.mybatisplus.core.metadata.IPage<com.platon.browser.bean.CustomToken>
     * @author huangyongpeng@matrixelements.com
     * @date 2021/4/25
     */
    IPage<CustomToken> selectListByType(IPage<CustomToken> page, @Param("type") String type);

    CustomTokenDetail selectDetailByAddress(@Param("address") String address);

    int batchInsertOrUpdateSelective(@Param("list") List<Token> list, @Param("selective") Token.Column... selective);

}