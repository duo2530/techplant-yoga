package com.techplant.yoga.membercard.service;

import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.query.MemberCardPublicQuery;
import com.techplant.yoga.membercard.query.MemberCardQuery;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import com.techplant.yoga.membercard.vo.MemberCardPublicItemVO;

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
}
