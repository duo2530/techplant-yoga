package com.techplant.yoga.membercard.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.query.MemberCardPublicQuery;
import com.techplant.yoga.membercard.query.MemberCardQuery;

/**
 * 会员卡数据访问语义。
 *
 * <p>屏蔽 Mapper 细节：筛选条件、字段选择与<b>稳定排序</b>在这里组装；
 * 删除是<b>物理删除</b>（{@code MemberCardDO} 不标 {@code @TableLogic}）。</p>
 */
public interface MemberCardDao {
    /** 管理端分页：筛选 memberId（精确）／cardNo（模糊）／cardType（精确）／storeId（适用门店精确），排序 id DESC */
    IPage<MemberCardDO> selectPage(MemberCardQuery query);

    /**
     * 用户端分页：固定按「所属会员」筛选，排序 id DESC
     *
     * <p>不带门店或状态条件：本人所有门店的现存卡都要返回。</p>
     */
    IPage<MemberCardDO> selectPageForMember(Long memberId, MemberCardPublicQuery query);

    /** 按主键查询（物理删除后查不到） */
    MemberCardDO selectById(Long cardId);

    /** 按会员与卡类型取会员卡 ID 集合（预约列表按卡类型筛选用） */
    List<Long> selectIdsByMemberAndType(Long memberId, Integer cardType);

    /** 按主键集合批量查询卡号、卡名与卡类型（跨模块 summaries 用） */
    List<MemberCardDO> selectByIds(Collection<Long> cardIds);

    /**
     * 按主键做当前读（{@code SELECT ... FOR UPDATE}）取卡；不存在返回 {@code null}
     *
     * <p>开卡与停卡必须先当前读锁定该行，再按读到的状态决定是否流转。</p>
     */
    MemberCardDO selectByIdForUpdate(Long cardId);

    /** 新增（主键雪花 ID、审计字段由公共机制填充） */
    int insert(MemberCardDO card);

    /**
     * 开卡：写入开卡时间、有效期单位与有效期起止
     *
     * <p>条件带上「尚未开卡」，状态已被并发改动时返回影响行数 0。</p>
     */
    int activate(Long cardId, Integer validPeriodUnit, LocalDateTime validStartTime, LocalDateTime validEndTime,
            LocalDateTime activateTime, Long updateBy);

    /**
     * 停卡：写入停卡时间与原因，保留剩余次数与有效期
     *
     * <p>条件带上「尚未停卡」，状态已被并发改动时返回影响行数 0。</p>
     */
    int stop(Long cardId, LocalDateTime stopTime, String stopReason, Long updateBy);

    /** 物理删除 */
    int deleteById(Long cardId);

    /**
     * 条件扣次：{@code remaining_times = remaining_times - persons}，且要求扣后不为负
     *
     * <p>并发扣次只有满足余额的行会被更新，余额不足或卡片已并发删除时返回影响行数 0。</p>
     */
    int deductTimes(Long cardId, Integer persons);

    /**
     * 返次：{@code remaining_times = remaining_times + times}
     *
     * <p>停卡或过期不阻止返次；卡片已删除时返回影响行数 0。</p>
     */
    int refundTimes(Long cardId, Integer times);
}
