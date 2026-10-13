package com.techplant.yoga.member.dao;

import java.time.LocalDateTime;
import com.techplant.yoga.member.domain.MemberDO;

/**
 * 会员数据访问。
 *
 * <p>只被 service 调用，屏蔽 mapper 细节；本表无 {@code deleted} 列，查询天然只含现存行。</p>
 */
public interface MemberDao
{
    /** 按手机号查会员（唯一键 uk_member_phone，登录／注册／忘记密码的入口查询） */
    MemberDO selectByPhone(String phone);

    /** 按会员 ID 查会员 */
    MemberDO selectById(Long memberId);

    /** 该会员是否存在且已注册（{@code password} 非空）——请求鉴权用 */
    boolean existsRegisteredById(Long memberId);

    /** 新增会员（主键雪花 ID、审计字段由公共机制填充） */
    int insert(MemberDO member);

    /**
     * 补齐已有未注册档案的密码：仅当 {@code password IS NULL} 时更新，返回影响行数
     *
     */
    int updatePasswordIfUnregistered(Long memberId, String passwordHash);

    /** 覆盖密码 */
    int updatePassword(Long memberId, String passwordHash);

    /** 刷新最后登录时间 */
    int updateLastLoginTime(Long memberId, LocalDateTime lastLoginTime);
}
