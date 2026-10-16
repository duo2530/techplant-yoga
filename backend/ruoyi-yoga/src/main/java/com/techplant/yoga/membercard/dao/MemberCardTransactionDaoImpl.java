package com.techplant.yoga.membercard.dao;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.techplant.yoga.membercard.domain.MemberCardTransactionDO;
import com.techplant.yoga.membercard.mapper.MemberCardTransactionMapper;

/** 会员卡次数流水数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class MemberCardTransactionDaoImpl implements MemberCardTransactionDao {
    private final MemberCardTransactionMapper memberCardTransactionMapper;

    @Override
    public int insert(MemberCardTransactionDO transaction) {
        return memberCardTransactionMapper.insert(transaction);
    }

    @Override
    public MemberCardTransactionDO selectByReservationAndType(Long reservationId, Integer changeType) {
        if (reservationId == null || changeType == null) {
            return null;
        }
        return memberCardTransactionMapper.selectOne(Wrappers.<MemberCardTransactionDO>lambdaQuery()
                .eq(MemberCardTransactionDO::getReservationId, reservationId)
                .eq(MemberCardTransactionDO::getChangeType, changeType));
    }

    @Override
    public List<MemberCardTransactionDO> selectByReservationIdsAndType(Collection<Long> reservationIds,
            Integer changeType) {
        if (reservationIds == null || reservationIds.isEmpty() || changeType == null) {
            return new ArrayList<MemberCardTransactionDO>();
        }
        return memberCardTransactionMapper.selectList(Wrappers.<MemberCardTransactionDO>lambdaQuery()
                .in(MemberCardTransactionDO::getReservationId, reservationIds)
                .eq(MemberCardTransactionDO::getChangeType, changeType));
    }
}
