package com.techplant.yoga.membercard.service.impl;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.membercard.dao.MemberCardTransactionDao;
import com.techplant.yoga.membercard.domain.MemberCardTransactionDO;
import com.techplant.yoga.membercard.enums.MemberCardChangeTypeEnum;
import com.techplant.yoga.membercard.service.MemberCardTransactionService;

/** 会员卡次数流水写入实现；调用方已处于预约事务中，这里不另开事务。 */
@Service
@RequiredArgsConstructor
public class MemberCardTransactionServiceImpl implements MemberCardTransactionService {
    /** 系统异常：流水写入失败 */
    private static final int INTERNAL_ERROR = 500;

    private final MemberCardTransactionDao memberCardTransactionDao;

    @Override
    public void recordDeduct(Long reservationId, Long memberCardId, Long memberId, Integer persons, Integer beforeTimes,
            Integer afterTimes) {
        append(reservationId, memberCardId, memberId, MemberCardChangeTypeEnum.DEDUCT, persons, -persons, beforeTimes,
                afterTimes);
    }

    @Override
    public void recordRefund(Long reservationId, Long memberCardId, Long memberId, Integer persons, Integer refundTimes,
            Integer beforeTimes, Integer afterTimes) {
        append(reservationId, memberCardId, memberId, MemberCardChangeTypeEnum.REFUND, persons, refundTimes,
                beforeTimes, afterTimes);
    }

    @Override
    public Integer deductedTimesOf(Long reservationId) {
        MemberCardTransactionDO deduct = memberCardTransactionDao.selectByReservationAndType(reservationId,
                MemberCardChangeTypeEnum.DEDUCT.getCode());
        return deduct == null || deduct.getChangeTimes() == null ? null : Math.abs(deduct.getChangeTimes());
    }

    @Override
    public Map<Long, Integer> deductedTimesOf(Collection<Long> reservationIds) {
        Map<Long, Integer> deductedTimes = new HashMap<Long, Integer>();
        List<MemberCardTransactionDO> deducts = memberCardTransactionDao.selectByReservationIdsAndType(reservationIds,
                MemberCardChangeTypeEnum.DEDUCT.getCode());
        for (MemberCardTransactionDO deduct : deducts) {
            if (deduct.getReservationId() != null && deduct.getChangeTimes() != null) {
                deductedTimes.put(deduct.getReservationId(), Math.abs(deduct.getChangeTimes()));
            }
        }
        return deductedTimes;
    }

    private void append(Long reservationId, Long memberCardId, Long memberId, MemberCardChangeTypeEnum changeType,
            Integer persons, Integer changeTimes, Integer beforeTimes, Integer afterTimes) {
        MemberCardTransactionDO transaction = new MemberCardTransactionDO();
        transaction.setReservationId(reservationId);
        transaction.setMemberCardId(memberCardId);
        transaction.setMemberId(memberId);
        transaction.setChangeType(changeType.getCode());
        transaction.setPersons(persons);
        transaction.setChangeTimes(changeTimes);
        transaction.setBeforeTimes(beforeTimes);
        transaction.setAfterTimes(afterTimes);
        if (memberCardTransactionDao.insert(transaction) != 1) {
            throw new ServiceException("会员卡次数流水写入失败", INTERNAL_ERROR);
        }
    }
}
