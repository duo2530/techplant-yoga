package com.techplant.yoga.member.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.query.MemberQuery;

/**
 * 会员数据访问。
 *
 * <p>只被 service 调用，屏蔽 mapper 细节；本表无 {@code deleted} 列，查询天然只含现存行。</p>
 */
public interface MemberDao
{
    /** 分页查询会员列表：后四位精确、显示名模糊、等级精确，按会员 ID 降序稳定排序 */
    IPage<MemberDO> selectPage(MemberQuery query);

    /** 按手机号查会员（唯一键 uk_member_phone，登录／注册／忘记密码的入口查询） */
    MemberDO selectByPhone(String phone);

    /** 按会员 ID 查会员 */
    MemberDO selectById(Long memberId);

    /** 按会员 ID 集合批量查询，仅取 id 与显示名（跨模块 summaries 用） */
    List<MemberDO> selectByIds(Collection<Long> memberIds);

    /**
     * 按会员 ID 做当前读（{@code SELECT ... FOR UPDATE}）取会员；不存在返回 {@code null}
     *
     * <p>与 {@link #selectById(Long)} 的快照读不同：并发删除已提交时当前读能看到行已不存在。</p>
     */
    MemberDO selectByIdForUpdate(Long memberId);

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

    /**
     * 只更新已提交的档案字段：传 {@code null} 表示该字段不更新，返回影响行数
     */
    int updateProfile(Long memberId, Integer gender, Integer level, Long updateBy);

    /** 按会员 ID 物理删除会员行（登录凭证随行删除） */
    int deleteById(Long memberId);
}
