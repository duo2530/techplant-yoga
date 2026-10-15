package com.techplant.yoga.member.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.dto.MemberUpdateDTO;
import com.techplant.yoga.member.query.MemberQuery;
import com.techplant.yoga.member.vo.MemberDetailVO;
import com.techplant.yoga.member.vo.MemberListItemVO;
import com.techplant.yoga.member.vo.MemberSummaryVO;

/**
 * 会员基础能力。
 *
 * <p>账号与会员共用 {@code t_member}，本接口承载「会员身份」相关的跨模块校验，
 * 登录态与会话由 {@code MemberTokenService} 负责。</p>
 */
public interface MemberService
{
    /** 查询会员列表（分页；后四位精确、显示名模糊、等级精确筛选，按会员 ID 降序） */
    PageResult<MemberListItemVO> page(MemberQuery query);

    /**
     * 查询会员详情
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员不存在或已被删除（404）
     */
    MemberDetailVO getById(Long memberId);

    /**
     * 新增会员档案（不建登录凭证，不返回新 ID）
     *
     * @throws com.ruoyi.common.exception.ServiceException 手机号已建档（409）、性别取值非法或等级不在字典 {@code member_level} 中（500）
     */
    void create(MemberCreateDTO dto);

    /**
     * 修改会员档案：只维护性别与等级，省略字段保留原值，显式提交 null 报 500
     *
     * @return 更新后的会员详情
     * @throws com.ruoyi.common.exception.ServiceException 会员不存在（404）、取值非法或提交 null（500）
     */
    MemberDetailVO update(Long memberId, MemberUpdateDTO dto);

    /**
     * 删除会员：登录凭证随会员行删除，既有登录态因会员不存在而失效
     *
     * @throws com.ruoyi.common.exception.ServiceException 会员不存在或已被删除（404）
     */
    void delete(Long memberId);

    /**
     * 按手机号补齐登录信息
     *
     * @param phone        手机号
     * @param passwordHash 已生成的 BCrypt 哈希（明文不进本方法）
     */
    void registerByPhone(String phone, String passwordHash);

    /** 会员是否存在（会员卡／预约等跨模块存在性校验用） */
    boolean existsById(Long memberId);

    /**
     * 批量查询会员摘要（id + 显示名 + 手机号），供会员卡与预约模块批量补 {@code memberName}／{@code memberPhone}
     *
     * @param memberIds 会员 ID 集合，可为 null／空
     * @return 命中的会员摘要；已删除会员不在结果中，入参为空时返回空列表（不返回 null）
     */
    List<MemberSummaryVO> summaries(Collection<Long> memberIds);

    /** 会员是否存在且已注册（{@code password} 非空）——请求鉴权用 */
    boolean isRegistered(Long memberId);

    /**
     * 在事务中当前读锁定会员行，用于同会员的预约创建串行校验重复与时间冲突
     *
     * @return 会员行存在时为 {@code true}
     */
    boolean lockForBooking(Long memberId);
}
