package com.techplant.yoga.member.service;

/**
 * 会员基础能力（会员管理详细设计 §3.2、用户端接口详细设计 §4.4）。
 *
 * <p>账号与会员共用 {@code t_member}，本接口承载「会员身份」相关的跨模块校验，
 * 登录态与会话由 {@code MemberTokenService} 负责。</p>
 */
public interface MemberService
{
    /**
     * 按手机号补齐登录信息（用户端注册，§4.4 第 2 步）
     *
     * <p>已有未注册档案：按会员 ID 且 {@code password IS NULL} 只更新密码，保留原会员 ID 与资料；
     * 无档案：新建 V0 会员（显示名 = 忻会员 + 手机号后 4 位）；已注册或并发冲突一律 409。</p>
     *
     * @param phone        手机号
     * @param passwordHash 已生成的 BCrypt 哈希（明文不进本方法）
     */
    void registerByPhone(String phone, String passwordHash);

    /** 会员是否存在（会员卡／预约等跨模块存在性校验用） */
    boolean existsById(Long memberId);

    /** 会员是否存在且已注册（{@code password} 非空）——请求鉴权用（§4.7 第 2 步） */
    boolean isRegistered(Long memberId);
}
