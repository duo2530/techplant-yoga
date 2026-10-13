package com.techplant.yoga.member.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.mapper.MemberMapper;

/**
 * 会员数据访问实现（会员管理详细设计 §3.2、§3.4）。
 *
 * <p>更新一律用 {@code lambdaUpdate} 显式 SET：MyBatis-Plus 的 {@code updateById} 会忽略 null，
 * 而本模块的更新（补齐密码、覆盖密码、刷新最后登录时间）都要精确控制「只动哪一列」。</p>
 *
 * <p>会员端操作没有管理端登录人，因此更新不写 {@code update_by}，只写 {@code update_time}；
 * 新增走 {@link MemberDO} 上的 {@code @TableField(fill = ...)} 由公共处理器填审计字段。</p>
 */
@Repository
public class MemberDaoImpl implements MemberDao
{
    private final MemberMapper memberMapper;

    public MemberDaoImpl(MemberMapper memberMapper)
    {
        this.memberMapper = memberMapper;
    }

    @Override
    public MemberDO selectByPhone(String phone)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            return null;
        }
        LambdaQueryWrapper<MemberDO> wrapper = Wrappers.<MemberDO>lambdaQuery()
                .eq(MemberDO::getPhone, phone.trim());
        List<MemberDO> members = memberMapper.selectList(wrapper);
        return members == null || members.isEmpty() ? null : members.get(0);
    }

    @Override
    public MemberDO selectById(Long memberId)
    {
        if (memberId == null)
        {
            return null;
        }
        return memberMapper.selectById(memberId);
    }

    @Override
    public boolean existsRegisteredById(Long memberId)
    {
        if (memberId == null)
        {
            return false;
        }
        Long count = memberMapper.selectCount(Wrappers.<MemberDO>lambdaQuery()
                .eq(MemberDO::getId, memberId)
                .isNotNull(MemberDO::getPassword)
                .ne(MemberDO::getPassword, ""));
        return count != null && count > 0;
    }

    @Override
    public int insert(MemberDO member)
    {
        return memberMapper.insert(member);
    }

    @Override
    public int updatePasswordIfUnregistered(Long memberId, String passwordHash)
    {
        // 条件里必须带 password IS NULL：并发注册时只有一个请求能补上凭证（§4.4 第 2 步）
        return memberMapper.update(null, Wrappers.<MemberDO>lambdaUpdate()
                .eq(MemberDO::getId, memberId)
                .isNull(MemberDO::getPassword)
                .set(MemberDO::getPassword, passwordHash)
                .set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updatePassword(Long memberId, String passwordHash)
    {
        return memberMapper.update(null, Wrappers.<MemberDO>lambdaUpdate()
                .eq(MemberDO::getId, memberId)
                .set(MemberDO::getPassword, passwordHash)
                .set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateLastLoginTime(Long memberId, LocalDateTime lastLoginTime)
    {
        return memberMapper.update(null, Wrappers.<MemberDO>lambdaUpdate()
                .eq(MemberDO::getId, memberId)
                .set(MemberDO::getLastLoginTime, lastLoginTime)
                .set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }
}
