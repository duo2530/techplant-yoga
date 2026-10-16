package com.techplant.yoga.membercard.dao;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.membercard.domain.MemberCardTransactionDO;

/** 会员卡次数流水数据访问语义。 */
public interface MemberCardTransactionDao {
    /** 追加一条流水（主键雪花 ID 由公共机制填充） */
    int insert(MemberCardTransactionDO transaction);

    /**
     * 按预约与流水类型查流水：取消预约时用它取原始扣次记录，返次金额以该记录为准
     *
     * @return 命中返回流水，没有返回 {@code null}
     */
    MemberCardTransactionDO selectByReservationAndType(Long reservationId, Integer changeType);

    /**
     * 按预约 ID 集合与流水类型批量查流水（预约列表批量取扣次用）
     *
     * @return 命中的流水；入参为空时返回空列表（不返回 null）
     */
    List<MemberCardTransactionDO> selectByReservationIdsAndType(Collection<Long> reservationIds, Integer changeType);
}
