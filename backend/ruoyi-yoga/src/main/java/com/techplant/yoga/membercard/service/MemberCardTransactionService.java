package com.techplant.yoga.membercard.service;

import java.util.Collection;
import java.util.Map;

/** 会员卡次数流水写入与查询能力：写入与预约、名额写入同一事务。 */
public interface MemberCardTransactionService
{
    /**
     * 记录预约扣次流水
     *
     * @param reservationId 预约ID
     * @param memberCardId  会员卡ID
     * @param memberId      会员ID
     * @param persons       预约人数
     * @param beforeTimes   扣次前剩余次数
     * @param afterTimes    扣次后剩余次数
     */
    void recordDeduct(Long reservationId, Long memberCardId, Long memberId, Integer persons, Integer beforeTimes,
            Integer afterTimes);

    /**
     * 记录取消返次流水
     *
     * @param reservationId 预约ID
     * @param memberCardId  会员卡ID
     * @param memberId      会员ID
     * @param persons       原预约人数
     * @param refundTimes   返还次数（正数）
     * @param beforeTimes   返次前剩余次数
     * @param afterTimes    返次后剩余次数
     */
    void recordRefund(Long reservationId, Long memberCardId, Long memberId, Integer persons, Integer refundTimes,
            Integer beforeTimes, Integer afterTimes);

    /**
     * 原预约扣次流水记录的扣次次数（正数）
     *
     * @return 无扣次流水（年卡或未扣次）返回 {@code null}
     */
    Integer deductedTimesOf(Long reservationId);

    /**
     * 批量取多个预约的扣次次数（预约列表展示原实际扣次用）
     *
     * @param reservationIds 预约 ID 集合，可为 null／空
     * @return 预约 ID → 扣次次数（正数）；无扣次流水的预约不在结果中
     */
    Map<Long, Integer> deductedTimesOf(Collection<Long> reservationIds);
}
