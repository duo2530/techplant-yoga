package com.techplant.yoga.membercard.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.query.MemberCardPublicQuery;
import com.techplant.yoga.membercard.query.MemberCardQuery;
import com.techplant.yoga.membercard.vo.MemberCardBookingSnapshot;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import com.techplant.yoga.membercard.vo.MemberCardPublicItemVO;
import com.techplant.yoga.membercard.vo.MemberCardSummaryVO;

/** 会员卡管理业务服务（管理端发卡、开卡、停卡、删除、列表与详情；用户端我的会员卡）。 */
public interface MemberCardService
{
    /** 管理端分页查询：按会员、卡号（模糊）、卡类型与适用门店筛选，最新发卡在前 */
    PageResult<MemberCardListItemVO> page(MemberCardQuery query);

    /**
     * 用户端分页查询「我的会员卡」：固定按登录会员过滤，包含本人所有门店的未开卡、正常、过期、停卡与已用尽卡
     *
     * <p>展示状态、总次数与年卡剩余天数都在这里按统一口径实时计算，无卡返回空列表。</p>
     */
    PageResult<MemberCardPublicItemVO> pageForMember(Long memberId, MemberCardPublicQuery query);

    /**
     * 查询会员卡详情；关联会员或门店已删除时仍返回卡片，关联信息按空字符串兜底
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在或已被删除（404）
     */
    MemberCardDetailVO getById(Long cardId);

    /**
     * 发卡（创建待开卡）：卡号由服务端生成，次数卡剩余次数初值为购买与赠送之和，不写有效期起止
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员／开卡门店／适用门店不存在（404）、
     *         卡号冲突（409）、字段组合或枚举非法（500）
     */
    void create(MemberCardCreateDTO dto);

    /**
     * 正式开卡：按当前自然日写开卡时间与有效期单位，有效期起止随之写入
     *
     * @return 开卡后的详情
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在（404）、已开卡或已停卡（409）
     */
    MemberCardDetailVO activate(Long cardId);

    /**
     * 停卡：写入停卡时间与原因，保留剩余次数与有效期
     *
     * @return 停卡后的详情
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在（404）、已停卡（409）
     */
    MemberCardDetailVO stop(Long cardId, MemberCardStopDTO dto);

    /**
     * 删除会员卡（物理删除）
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在或已被删除（404）
     */
    void delete(Long cardId);

    /**
     * 预约事务内锁定会员卡并校验可约资格
     *
     * <p>锁顺序由预约模块编排，方法内部当前读锁定该卡行后校验归属、开卡、停卡、有效期、
     * 适用门店与适用课种；返回的剩余次数是锁定时读到的值，扣次以条件更新为准。</p>
     *
     * @param courseType       排课课种快照：1团课 2精品课 3私教课 4特色课
     * @param scheduleStoreId  排课门店 ID
     * @param startTime        课程开始时间（快照）
     * @param endTime          课程结束时间（快照）
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在或不属于本人（404）；
     *         未开卡、已停卡、已过期、有效期不覆盖课程、门店或课种不适用、次数不足（409）
     */
    MemberCardBookingSnapshot lockBookableCard(Long memberCardId, Long memberId, Integer courseType,
            Long scheduleStoreId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 预约事务内锁定会员卡行并校验归属，不做可约资格校验
     *
     * <p>取消预约返次时用它与排课、预约保持同一加锁顺序；停卡或过期不阻止返次。</p>
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员卡不存在或不属于本人（404）
     */
    MemberCardBookingSnapshot lockCard(Long memberCardId, Long memberId);

    /**
     * 按卡类型取本人会员卡 ID 集合，供预约列表按卡类型筛选
     *
     * @return 命中的会员卡 ID；无卡返回空列表（不返回 null）
     */
    List<Long> cardIdsOfMember(Long memberId, Integer cardType);

    /**
     * 批量查询会员卡摘要（id + 卡号 + 卡名 + 卡类型），供预约管理端列表补卡展示字段
     *
     * @param cardIds 会员卡 ID 集合，可为 null／空
     * @return 命中的摘要；已删除的卡不在结果中，入参为空时返回空列表（不返回 null）
     */
    List<MemberCardSummaryVO> summaries(Collection<Long> cardIds);

    /**
     * 按人数扣次：条件更新剩余次数，余额不足返回 {@code false}
     *
     * <p>年卡不调用本方法。</p>
     */
    boolean deductTimes(Long memberCardId, Integer persons);

    /**
     * 返次：把原扣次流水记录的次数返还原卡，卡片已删除时返回 {@code false}
     *
     * <p>停卡或过期不阻止返次。</p>
     */
    boolean refundTimes(Long memberCardId, Integer times);
}
