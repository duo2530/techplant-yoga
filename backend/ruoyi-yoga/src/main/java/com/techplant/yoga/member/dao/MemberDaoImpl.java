package com.techplant.yoga.member.dao;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.mapper.MemberMapper;
import com.techplant.yoga.member.query.MemberQuery;

/** 会员数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class MemberDaoImpl implements MemberDao {
    private final MemberMapper memberMapper;

    @Override
    public IPage<MemberDO> selectPage(MemberQuery query) {
        // 列表要按 password 推导 registered，哈希只留在 service 内，不进 VO
        LambdaQueryWrapper<MemberDO> wrapper = Wrappers.<MemberDO>lambdaQuery().select(MemberDO::getId,
                MemberDO::getPhone, MemberDO::getPassword, MemberDO::getName, MemberDO::getGender, MemberDO::getLevel,
                MemberDO::getCreateTime, MemberDO::getUpdateTime);
        String phoneLast4 = trimToNull(query.getPhoneLast4());
        wrapper.eq(phoneLast4 != null, MemberDO::getPhoneLast4, phoneLast4);
        String name = trimToNull(query.getName());
        wrapper.like(name != null, MemberDO::getName, name);
        wrapper.eq(query.getLevel() != null, MemberDO::getLevel, query.getLevel());
        // 稳定排序：id 降序（新建会员在最前）
        wrapper.orderByDesc(MemberDO::getId);

        Page<MemberDO> page = new Page<MemberDO>(query.getPageNum(), query.getPageSize());
        return memberMapper.selectPage(page, wrapper);
    }

    @Override
    public MemberDO selectByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<MemberDO> wrapper = Wrappers.<MemberDO>lambdaQuery().eq(MemberDO::getPhone, phone.trim());
        List<MemberDO> members = memberMapper.selectList(wrapper);
        return members == null || members.isEmpty() ? null : members.get(0);
    }

    @Override
    public MemberDO selectById(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberMapper.selectById(memberId);
    }

    @Override
    public List<MemberDO> selectByIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MemberDO> wrapper = Wrappers.<MemberDO>lambdaQuery()
                .select(MemberDO::getId, MemberDO::getName, MemberDO::getPhone).in(MemberDO::getId, memberIds);
        List<MemberDO> members = memberMapper.selectList(wrapper);
        return members == null ? Collections.<MemberDO>emptyList() : members;
    }

    @Override
    public MemberDO selectByIdForUpdate(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberMapper
                .selectOne(Wrappers.<MemberDO>lambdaQuery().eq(MemberDO::getId, memberId).last("FOR UPDATE"));
    }

    @Override
    public boolean existsRegisteredById(Long memberId) {
        if (memberId == null) {
            return false;
        }
        Long count = memberMapper.selectCount(Wrappers.<MemberDO>lambdaQuery().eq(MemberDO::getId, memberId)
                .isNotNull(MemberDO::getPassword).ne(MemberDO::getPassword, ""));
        return count != null && count > 0;
    }

    @Override
    public int insert(MemberDO member) {
        return memberMapper.insert(member);
    }

    @Override
    public int updatePasswordIfUnregistered(Long memberId, String passwordHash) {
        return memberMapper.update(null,
                Wrappers.<MemberDO>lambdaUpdate().eq(MemberDO::getId, memberId).isNull(MemberDO::getPassword)
                        .set(MemberDO::getPassword, passwordHash).set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updatePassword(Long memberId, String passwordHash) {
        return memberMapper.update(null, Wrappers.<MemberDO>lambdaUpdate().eq(MemberDO::getId, memberId)
                .set(MemberDO::getPassword, passwordHash).set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateLastLoginTime(Long memberId, LocalDateTime lastLoginTime) {
        return memberMapper.update(null, Wrappers.<MemberDO>lambdaUpdate().eq(MemberDO::getId, memberId)
                .set(MemberDO::getLastLoginTime, lastLoginTime).set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateProfile(Long memberId, Integer gender, Integer level, Long updateBy) {
        return memberMapper.update(null,
                Wrappers.<MemberDO>lambdaUpdate().eq(MemberDO::getId, memberId)
                        .set(gender != null, MemberDO::getGender, gender).set(level != null, MemberDO::getLevel, level)
                        .set(MemberDO::getUpdateBy, updateBy).set(MemberDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long memberId) {
        // 物理删除：MemberDO 没有 @TableLogic，生成 DELETE FROM t_member WHERE id = ?
        return memberMapper.deleteById(memberId);
    }

    /**
     * 去除字符串前后空格；空串归一为 {@code null}（空值条件不参与筛选）
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
