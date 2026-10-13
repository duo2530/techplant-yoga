package com.techplant.yoga.member.service.impl;

import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.LoginDTO;
import com.techplant.yoga.member.dto.RegisterDTO;
import com.techplant.yoga.member.dto.ResetPasswordDTO;
import com.techplant.yoga.member.dto.SmsCodeSendDTO;
import com.techplant.yoga.member.enums.SmsSceneEnum;
import com.techplant.yoga.member.service.AccountService;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.service.MemberTokenService;
import com.techplant.yoga.member.service.SmsCodeService;
import com.techplant.yoga.member.vo.LoginVO;

/**
 * 用户端账号业务实现（用户端接口详细设计 §4.3～§4.6）。
 *
 * <p><b>登录失败口径</b>：手机号不存在、尚未注册（{@code password} 为空）、密码错误
 * 三者<b>统一</b>返回 409「手机号或密码错误」，不泄露手机号是否注册（§4.5 第 1 步）。</p>
 *
 * <p><b>验证码消费时机</b>：注册与忘记密码都在「验证码校验成功」后才消费，
 * 校验失败保留未过期验证码供重试（§4.4 第 1 步、§4.6 第 1 步）。</p>
 *
 * <p><b>密码编码器</b>：注入框架（{@code SecurityConfig}）里那一个
 * {@link BCryptPasswordEncoder} Bean，保证会员密码与管理端密码的编码强度一致。</p>
 */
@Service
public class AccountServiceImpl implements AccountService
{
    /** 资源不存在：忘记密码时手机号未注册 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：验证码错误／过期、注册冲突、登录失败 */
    private static final int CONFLICT = 409;

    /** 参数校验失败／系统异常 */
    private static final int PARAM_INVALID = 500;

    /** 密码强度：8～20 位且同时包含字母与数字（与 DTO 上的校验规则一致，防 service 直调） */
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$";

    private final MemberDao memberDao;

    private final MemberService memberService;

    private final SmsCodeService smsCodeService;

    private final MemberTokenService memberTokenService;

    private final BCryptPasswordEncoder passwordEncoder;

    public AccountServiceImpl(MemberDao memberDao, MemberService memberService, SmsCodeService smsCodeService,
            MemberTokenService memberTokenService, BCryptPasswordEncoder passwordEncoder)
    {
        this.memberDao = memberDao;
        this.memberService = memberService;
        this.smsCodeService = smsCodeService;
        this.memberTokenService = memberTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void sendSmsCode(SmsCodeSendDTO dto)
    {
        SmsCodeService.requireValidScene(dto.getScene());
        String phone = normalizePhone(dto.getPhone());
        SmsSceneEnum scene = SmsSceneEnum.fromCode(dto.getScene());
        MemberDO member = memberDao.selectByPhone(phone);

        if (scene == SmsSceneEnum.REGISTER && member != null && member.isRegistered())
        {
            // 注册场景：手机号已注册 → 409（§2.2.4）
            throw new ServiceException("该手机号已注册，请直接登录", CONFLICT);
        }
        if (scene == SmsSceneEnum.RESET && (member == null || !member.isRegistered()))
        {
            // 忘记密码场景：手机号未注册 → 404（§2.2.4）
            throw new ServiceException("手机号未注册", NOT_FOUND);
        }

        smsCodeService.send(scene.getCode(), phone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto)
    {
        String phone = normalizePhone(dto.getPhone());
        validatePasswordStrength(dto.getPassword());
        // 验证码校验成功才消费（错误／过期返回 409，保留未过期验证码供重试）
        smsCodeService.verifyAndConsume(SmsSceneEnum.REGISTER.getCode(), phone, dto.getSmsCode());
        // 注册成功不签发 token、不刷新最后登录时间（§4.4 第 4 步）
        memberService.registerByPhone(phone, passwordEncoder.encode(dto.getPassword()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto)
    {
        String phone = normalizePhone(dto.getPhone());
        MemberDO member = memberDao.selectByPhone(phone);
        if (member == null || !member.isRegistered()
                || !passwordEncoder.matches(dto.getPassword(), member.getPassword()))
        {
            throw new ServiceException("手机号或密码错误", CONFLICT);
        }

        // 先刷新最后登录时间，数据库更新成功后才签发登录态（§4.5 第 2 步）
        memberDao.updateLastLoginTime(member.getId(), LocalDateTime.now());
        return memberTokenService.createToken(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto)
    {
        String phone = normalizePhone(dto.getPhone());
        validatePasswordStrength(dto.getPassword());
        MemberDO member = memberDao.selectByPhone(phone);
        if (member == null || !member.isRegistered())
        {
            throw new ServiceException("手机号未注册", NOT_FOUND);
        }
        smsCodeService.verifyAndConsume(SmsSceneEnum.RESET.getCode(), phone, dto.getSmsCode());
        // 覆盖哈希；本阶段不批量撤销已有登录态（§4.6 第 1 步）
        memberDao.updatePassword(member.getId(), passwordEncoder.encode(dto.getPassword()));
    }

    /** 密码强度兜底校验（接口层 @Valid 已拦一层） */
    private void validatePasswordStrength(String password)
    {
        if (password == null || !password.matches(PASSWORD_PATTERN))
        {
            throw new ServiceException("密码需 8～20 位且同时包含字母与数字", PARAM_INVALID);
        }
    }

    /** 手机号规整与兜底校验：接口层已校验格式，这里防住 service 直调（null／空白 → 500） */
    private String normalizePhone(String phone)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException("手机号不能为空", PARAM_INVALID);
        }
        return phone.trim();
    }
}
