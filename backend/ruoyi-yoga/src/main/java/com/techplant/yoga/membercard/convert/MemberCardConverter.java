package com.techplant.yoga.membercard.convert;

import java.util.ArrayList;
import java.util.List;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.enums.CourseScopeEnum;
import com.techplant.yoga.membercard.enums.MemberCardPublicStatusEnum;
import com.techplant.yoga.membercard.enums.MemberCardTypeEnum;
import com.techplant.yoga.membercard.vo.MemberCardBookingSnapshot;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import com.techplant.yoga.membercard.vo.MemberCardPublicItemVO;
import com.techplant.yoga.membercard.vo.MemberCardSummaryVO;

/**
 * 会员卡对象转换。
 *
 * <p>转换是<b>纯函数</b>：不查库、不查服务。卡号、有效期、剩余次数与展示状态由 service 按业务规则算好后传入；
 * 会员名与门店名由 service 批量查好后以入参传入，关联对象已删除时传 {@code null}（翻译为空字符串）。</p>
 */
public final class MemberCardConverter
{
    private MemberCardConverter() { }

    /** 发卡请求 → DO：卡号、有效期数量与剩余次数由 service 计算后传入 */
    public static MemberCardDO toDO(MemberCardCreateDTO dto, MemberCardTypeEnum cardType, String cardNo,
            Integer validPeriodCount)
    {
        MemberCardDO card = new MemberCardDO();
        card.setMemberId(dto.getMemberId());
        card.setOpenStoreId(dto.getOpenStoreId());
        card.setCardNo(cardNo);
        card.setCardName(trim(dto.getCardName()));
        card.setCardType(cardType.getCode());
        card.setCourseScope(dto.getCourseScope());
        card.setStoreId(cardType.isTimesCard() ? dto.getStoreId() : null);
        // 次数卡：剩余次数初值为购买 + 赠送；年卡不写次数字段
        card.setPurchaseTimes(cardType.isTimesCard() ? dto.getPurchaseTimes() : null);
        card.setGiftTimes(cardType.isTimesCard() ? dto.getGiftTimes() : null);
        card.setRemainingTimes(cardType.isTimesCard() ? dto.getPurchaseTimes() + dto.getGiftTimes() : null);
        // 有效期单位与起止时间在正式开卡时写入，发卡只保存有效期数量
        card.setValidPeriodCount(validPeriodCount);
        return card;
    }

    /** 单行 → 列表项 */
    public static MemberCardListItemVO toListItemVO(MemberCardDO card, String memberName, String openStoreNo,
            String openStoreName, String storeName, Integer remainingDays)
    {
        MemberCardListItemVO vo = new MemberCardListItemVO();
        fill(vo, card, memberName, openStoreNo, openStoreName, storeName, remainingDays);
        return vo;
    }

    /** 单行 → 详情：字段与列表项一致 */
    public static MemberCardDetailVO toDetailVO(MemberCardDO card, String memberName, String openStoreNo,
            String openStoreName, String storeName, Integer remainingDays)
    {
        MemberCardDetailVO vo = new MemberCardDetailVO();
        fill(vo, card, memberName, openStoreNo, openStoreName, storeName, remainingDays);
        return vo;
    }

    /**
     * 单行 → 我的会员卡列表项
     *
     * @param status 展示状态（service 按停卡、开卡、有效期与剩余次数判定）
     * @param storeName 适用门店名称；年卡或门店已删除时传 {@code null}
     * @param storeScopeText 适用门店文案（门店名称／适用门店已删除／全部门店通用）
     * @param remainingDays 年卡剩余天数，其余情况传 {@code null}
     */
    public static MemberCardPublicItemVO toPublicItemVO(MemberCardDO card, MemberCardPublicStatusEnum status,
            String storeName, String storeScopeText, Integer remainingDays)
    {
        MemberCardPublicItemVO vo = new MemberCardPublicItemVO();
        vo.setId(card.getId());
        vo.setCardNo(card.getCardNo());
        vo.setCardName(card.getCardName());
        vo.setCardType(card.getCardType());
        vo.setCardTypeText(MemberCardTypeEnum.nameOf(card.getCardType()));
        vo.setCardCategoryText(MemberCardTypeEnum.categoryNameOf(card.getCardType()));
        vo.setStatus(status.getCode());
        vo.setStatusText(status.getText());
        vo.setCourseScope(card.getCourseScope());
        vo.setCourseScopeName(CourseScopeEnum.nameOf(card.getCourseScope()));
        vo.setStoreId(card.getStoreId());
        // 年卡没有适用门店，名称为 null；次数卡的适用门店被删除时同样为 null（文案由 storeScopeText 承担）
        vo.setStoreName(card.getStoreId() == null ? null : storeName);
        vo.setStoreScopeText(storeScopeText);
        vo.setPurchaseTimes(card.getPurchaseTimes());
        vo.setGiftTimes(card.getGiftTimes());
        vo.setTotalTimes(totalTimes(card));
        vo.setRemainingTimes(card.getRemainingTimes());
        vo.setRemainingDays(remainingDays);
        vo.setActivateTime(card.getActivateTime());
        vo.setValidStartTime(card.getValidStartTime());
        vo.setValidEndTime(card.getValidEndTime());
        return vo;
    }

    /** 单行 → 预约事务用的锁卡快照（只带资格校验与扣返次需要的字段） */
    public static MemberCardBookingSnapshot toBookingSnapshot(MemberCardDO card)
    {
        MemberCardBookingSnapshot snapshot = new MemberCardBookingSnapshot();
        snapshot.setId(card.getId());
        snapshot.setMemberId(card.getMemberId());
        snapshot.setCardName(card.getCardName());
        snapshot.setCardType(card.getCardType());
        snapshot.setCourseScope(card.getCourseScope());
        snapshot.setStoreId(card.getStoreId());
        snapshot.setValidStartTime(card.getValidStartTime());
        snapshot.setValidEndTime(card.getValidEndTime());
        snapshot.setActivateTime(card.getActivateTime());
        snapshot.setStopTime(card.getStopTime());
        snapshot.setRemainingTimes(card.getRemainingTimes());
        return snapshot;
    }

    /** 批量 → 会员卡摘要（跨模块 summaries，给卡号、卡名与卡类型） */
    public static List<MemberCardSummaryVO> toSummaryVOList(List<MemberCardDO> cards)
    {
        List<MemberCardSummaryVO> list = new ArrayList<MemberCardSummaryVO>();
        if (cards != null)
        {
            for (MemberCardDO card : cards)
            {
                list.add(new MemberCardSummaryVO(card.getId(), card.getCardNo(), card.getCardName(),
                        card.getCardType()));
            }
        }
        return list;
    }

    private static void fill(MemberCardListItemVO vo, MemberCardDO card, String memberName, String openStoreNo,
            String openStoreName, String storeName, Integer remainingDays)
    {
        vo.setId(card.getId());
        vo.setCardNo(card.getCardNo());
        vo.setOpenStoreId(card.getOpenStoreId());
        vo.setOpenStoreNo(nullToEmpty(openStoreNo));
        vo.setOpenStoreName(nullToEmpty(openStoreName));
        vo.setCardName(card.getCardName());
        vo.setCardType(card.getCardType());
        vo.setCardTypeText(MemberCardTypeEnum.nameOf(card.getCardType()));
        vo.setCourseScope(card.getCourseScope());
        vo.setCourseScopeName(CourseScopeEnum.nameOf(card.getCourseScope()));
        vo.setStoreId(card.getStoreId());
        // 年卡没有适用门店（storeId 为空）时名称为 null；次数卡的适用门店被删除时给空字符串
        vo.setStoreName(card.getStoreId() == null ? null : nullToEmpty(storeName));
        vo.setMemberId(card.getMemberId());
        vo.setMemberName(nullToEmpty(memberName));
        vo.setPurchaseTimes(card.getPurchaseTimes());
        vo.setGiftTimes(card.getGiftTimes());
        vo.setRemainingTimes(card.getRemainingTimes());
        vo.setTotalTimes(totalTimes(card));
        vo.setValidStartTime(card.getValidStartTime());
        vo.setValidEndTime(card.getValidEndTime());
        vo.setRemainingDays(remainingDays);
        vo.setActivateTime(card.getActivateTime());
        vo.setStopTime(card.getStopTime());
        vo.setStopReason(card.getStopReason());
        vo.setCreateTime(card.getCreateTime());
    }

    /** 总次数 = 购买 + 赠送；次数字段缺失（非次数卡）时不给值 */
    private static Integer totalTimes(MemberCardDO card)
    {
        if (card.getPurchaseTimes() == null || card.getGiftTimes() == null)
        {
            return null;
        }
        return card.getPurchaseTimes() + card.getGiftTimes();
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
