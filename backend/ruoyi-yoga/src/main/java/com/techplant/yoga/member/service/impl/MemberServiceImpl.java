package com.techplant.yoga.member.service.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.service.MemberService;

/**
 * 会员基础能力实现（会员管理详细设计 §3.2、§4.6；用户端接口详细设计 §4.4）。
 *
 * <p><b>「未注册」= {@code password} 为空</b>：管理端可以只建会员档案不设密码，
 * 用户端注册时补齐同一行的登录信息，因此这里绝不重建档案、不覆盖会员 ID 与资料。</p>
 *
 * <p><b>并发口径</b>：补齐密码用 {@code password IS NULL} 作为条件（影响行数为 0 即 409），
 * 新建会员由 {@code uk_member_phone} 兜底（{@link DuplicateKeyException} 即 409），
 * 两种情况都不覆盖并发结果（用户端接口详细设计 §4.4 第 3 步）。</p>
 */
@Service
public class MemberServiceImpl implements MemberService
{
    /** 状态冲突：手机号已注册／并发注册冲突 */
    private static final int CONFLICT = 409;

    /** 参数校验失败／系统异常 */
    private static final int PARAM_INVALID = 500;

    /** 显示名前缀：忻会员 + 手机号后 4 位（BR-会员-010） */
    private static final String NAME_PREFIX = "忻会员";

    private final MemberDao memberDao;

    public MemberServiceImpl(MemberDao memberDao)
    {
        this.memberDao = memberDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerByPhone(String phone, String passwordHash)
    {
        String normalizedPhone = normalizePhone(phone);
        if (passwordHash == null || passwordHash.trim().isEmpty())
        {
            throw new ServiceException("密码不能为空", PARAM_INVALID);
        }

        MemberDO existing = memberDao.selectByPhone(normalizedPhone);
        if (existing != null)
        {
            // 已注册：拒绝重复注册
            if (existing.isRegistered())
            {
                throw new ServiceException("该手机号已注册", CONFLICT);
            }
            // 未注册档案：只补密码，保留原会员 ID、手机号、显示名、性别、等级与业务关联
            int rows = memberDao.updatePasswordIfUnregistered(existing.getId(), passwordHash);
            if (rows != 1)
            {
                // 影响行数为 0：并发下已被注册（或档案刚被删除），不覆盖别人的结果
                throw new ServiceException("该手机号已注册", CONFLICT);
            }
            return;
        }

        String phoneLast4 = last4(normalizedPhone);
        MemberDO member = new MemberDO();
        member.setPhone(normalizedPhone);
        member.setPassword(passwordHash);
        member.setPhoneLast4(phoneLast4);
        member.setName(NAME_PREFIX + phoneLast4);
        member.setGender(0);
        member.setLevel(0);
        try
        {
            int rows = memberDao.insert(member);
            if (rows != 1)
            {
                throw new ServiceException("注册失败，请稍后重试", PARAM_INVALID);
            }
        }
        catch (DuplicateKeyException e)
        {
            // uk_member_phone 并发兜底：唯一键冲突同样 409，不覆盖并发建档结果
            ServiceException exception = new ServiceException("该手机号已注册", CONFLICT);
            exception.initCause(e);
            throw exception;
        }
    }

    @Override
    public boolean existsById(Long memberId)
    {
        return memberDao.selectById(memberId) != null;
    }

    @Override
    public boolean isRegistered(Long memberId)
    {
        return memberDao.existsRegisteredById(memberId);
    }

    /** 手机号规整与兜底校验：接口层已校验格式，这里防住 service 直调 */
    private String normalizePhone(String phone)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException("手机号不能为空", PARAM_INVALID);
        }
        String normalized = phone.trim();
        if (normalized.length() < 4)
        {
            throw new ServiceException("手机号格式不正确", PARAM_INVALID);
        }
        return normalized;
    }

    /** 手机号后 4 位（phone_last4 与显示名共用） */
    private String last4(String phone)
    {
        return phone.substring(phone.length() - 4);
    }
}
