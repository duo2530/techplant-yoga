package com.techplant.yoga.membercard.dao;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.mapper.MemberCardMapper;
import com.techplant.yoga.membercard.query.MemberCardPublicQuery;
import com.techplant.yoga.membercard.query.MemberCardQuery;

/**
 * 会员卡数据访问实现。
 *
 * <p>本表<b>没有 deleted 字段</b>，所有查询天然返回全部有效行；删除走物理 {@code DELETE}。</p>
 */
@Repository
@RequiredArgsConstructor
public class MemberCardDaoImpl implements MemberCardDao {
    private final MemberCardMapper memberCardMapper;

    @Override
    public IPage<MemberCardDO> selectPage(MemberCardQuery query) {
        String cardNo = trimToNull(query.getCardNo());
        LambdaQueryWrapper<MemberCardDO> wrapper = Wrappers.<MemberCardDO>lambdaQuery()
                .eq(query.getMemberId() != null, MemberCardDO::getMemberId, query.getMemberId())
                .like(cardNo != null, MemberCardDO::getCardNo, cardNo)
                .eq(query.getCardType() != null, MemberCardDO::getCardType, query.getCardType())
                .eq(query.getStoreId() != null, MemberCardDO::getStoreId, query.getStoreId())
                // 稳定排序：最新发卡在前
                .orderByDesc(MemberCardDO::getId);
        return memberCardMapper.selectPage(new Page<MemberCardDO>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Override
    public IPage<MemberCardDO> selectPageForMember(Long memberId, MemberCardPublicQuery query) {
        LambdaQueryWrapper<MemberCardDO> wrapper = Wrappers.<MemberCardDO>lambdaQuery()
                .eq(MemberCardDO::getMemberId, memberId)
                // 稳定排序：最新发卡在前
                .orderByDesc(MemberCardDO::getId);
        return memberCardMapper.selectPage(new Page<MemberCardDO>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Override
    public MemberCardDO selectById(Long cardId) {
        if (cardId == null) {
            return null;
        }
        return memberCardMapper.selectById(cardId);
    }

    @Override
    public List<Long> selectIdsByMemberAndType(Long memberId, Integer cardType) {
        List<MemberCardDO> cards = memberCardMapper.selectList(
                Wrappers.<MemberCardDO>lambdaQuery().select(MemberCardDO::getId).eq(MemberCardDO::getMemberId, memberId)
                        .eq(cardType != null, MemberCardDO::getCardType, cardType));
        List<Long> ids = new ArrayList<Long>();
        for (MemberCardDO card : cards) {
            ids.add(card.getId());
        }
        return ids;
    }

    @Override
    public List<MemberCardDO> selectByIds(Collection<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return new ArrayList<MemberCardDO>();
        }
        return memberCardMapper
                .selectList(Wrappers.<MemberCardDO>lambdaQuery().select(MemberCardDO::getId, MemberCardDO::getCardNo,
                        MemberCardDO::getCardName, MemberCardDO::getCardType).in(MemberCardDO::getId, cardIds));
    }

    @Override
    public MemberCardDO selectByIdForUpdate(Long cardId) {
        if (cardId == null) {
            return null;
        }
        return memberCardMapper
                .selectOne(Wrappers.<MemberCardDO>lambdaQuery().eq(MemberCardDO::getId, cardId).last("FOR UPDATE"));
    }

    @Override
    public int insert(MemberCardDO card) {
        return memberCardMapper.insert(card);
    }

    @Override
    public int activate(Long cardId, Integer validPeriodUnit, LocalDateTime validStartTime, LocalDateTime validEndTime,
            LocalDateTime activateTime, Long updateBy) {
        return memberCardMapper.update(null, Wrappers.<MemberCardDO>lambdaUpdate().eq(MemberCardDO::getId, cardId)
                .isNull(MemberCardDO::getActivateTime).isNull(MemberCardDO::getStopTime)
                .set(MemberCardDO::getActivateTime, activateTime).set(MemberCardDO::getValidPeriodUnit, validPeriodUnit)
                .set(MemberCardDO::getValidStartTime, validStartTime).set(MemberCardDO::getValidEndTime, validEndTime)
                .set(MemberCardDO::getUpdateBy, updateBy).set(MemberCardDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int stop(Long cardId, LocalDateTime stopTime, String stopReason, Long updateBy) {
        return memberCardMapper.update(null,
                Wrappers.<MemberCardDO>lambdaUpdate().eq(MemberCardDO::getId, cardId).isNull(MemberCardDO::getStopTime)
                        .set(MemberCardDO::getStopTime, stopTime).set(MemberCardDO::getStopReason, stopReason)
                        .set(MemberCardDO::getUpdateBy, updateBy)
                        .set(MemberCardDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long cardId) {
        // 物理删除：MemberCardDO 没有 @TableLogic，生成 DELETE FROM t_member_card WHERE id = ?
        return memberCardMapper.deleteById(cardId);
    }

    @Override
    public int deductTimes(Long cardId, Integer persons) {
        return memberCardMapper.update(null, Wrappers.<MemberCardDO>lambdaUpdate().eq(MemberCardDO::getId, cardId)
                // 条件更新保证余额与并发安全：扣后为负的行不更新
                .apply("remaining_times >= {0}", persons).setSql("remaining_times = remaining_times - " + persons)
                .set(MemberCardDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int refundTimes(Long cardId, Integer times) {
        return memberCardMapper.update(null,
                Wrappers.<MemberCardDO>lambdaUpdate().eq(MemberCardDO::getId, cardId)
                        .setSql("remaining_times = remaining_times + " + times)
                        .set(MemberCardDO::getUpdateTime, LocalDateTime.now()));
    }

    /** 去除字符串前后空格；空串归一为 {@code null}（空值条件不参与筛选） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
