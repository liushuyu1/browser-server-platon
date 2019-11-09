package com.platon.browser.dao.mapper;//package com.platon.browser.dao.mapper.mapper_old;

import com.platon.browser.dao.entity.Proposal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomProposalMapper {
    int updateProposalList( @Param("proposalList") List <Proposal> list );
}
