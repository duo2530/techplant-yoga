package com.techplant.yoga.member.dao;

import java.time.LocalDateTime;
import com.techplant.yoga.member.domain.MemberDO;

/**
 * 会员数据访问（会员管理详细设计 §3.2、用户端接口详细设计 §4.4～§4.6）。
 *
 * <p>只被 service 调用，屏蔽 mapper 细节；本表无 {@code deleted} 列，查询天然只含现存行。</p>
 */
public interface MemberDao
{
    /** 按手机号查会员（唯一键 uk_member_phone，登录／注册／忘记密码的入口查询） */
    MemberDO selectByPhone(String phone);

    /** 按会员 ID 查会员 */
    MemberDO selectById(Long memberId);

    /** 该会员是否存在且已注册（{@code password} 非空）——请求鉴权用（§4.7 第 2 步） */
    boolean existsRegisteredById(Long memberId);

    /** 新增会员（主键雪花 ID、审计字段由公共机制填充） */
    int insert(MemberDO member);

    /**
     * 补齐已有未注册档案的密码：仅当 {@code password IS NULL} 时更新，返回影响行数
     * （§4.4 第 2 步；影响行数为 0 说明并发下已被别人注册，调用方按 409 处理）
     */
    int updatePasswordIfUnregistered(Long memberId, String passwordHash);

    /** 覆盖密码（忘记密码，§4.6 第 1 步） */
    int updatePassword(Long memberId, String passwordHash);

    /** 刷新最后登录时间（仅登录成功调用，§4.5 第 2 步） */
    int updateLastLoginTime(Long memberId, LocalDateTime lastLoginTime);
}
