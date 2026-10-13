package com.techplant.yoga.member.service;

/**
 * 会员基础能力。
 *
 * <p>账号与会员共用 {@code t_member}，本接口承载「会员身份」相关的跨模块校验，
 * 登录态与会话由 {@code MemberTokenService} 负责。</p>
 */
public interface MemberService
{
    /**
     * 按手机号补齐登录信息
     *
     * @param phone        手机号
     * @param passwordHash 已生成的 BCrypt 哈希（明文不进本方法）
     */
    void registerByPhone(String phone, String passwordHash);

    /** 会员是否存在（会员卡／预约等跨模块存在性校验用） */
    boolean existsById(Long memberId);

    /** 会员是否存在且已注册（{@code password} 非空）——请求鉴权用 */
    boolean isRegistered(Long memberId);
}
