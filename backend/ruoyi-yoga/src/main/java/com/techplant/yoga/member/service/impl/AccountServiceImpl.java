package com.techplant.yoga.member.service.impl;

import lombok.RequiredArgsConstructor;

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

/** 用户端账号业务实现。 */
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    /** 资源不存在：忘记密码时手机号未注册 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：验证码错误／过期、注册冲突、登录失败 */
    private static final int CONFLICT = 409;

    /** 参数校验失败 */
    private static final int PARAM_INVALID = 400;

    /** 密码强度：8～20 位且同时包含字母与数字（与 DTO 上的校验规则一致，防 service 直调） */
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$";

    private final MemberDao memberDao;

    private final MemberService memberService;

    private final SmsCodeService smsCodeService;

    private final MemberTokenService memberTokenService;

    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void sendSmsCode(SmsCodeSendDTO dto) {
        SmsCodeService.requireValidScene(dto.getScene());
        String phone = normalizePhone(dto.getPhone());
        SmsSceneEnum scene = SmsSceneEnum.fromCode(dto.getScene());
        MemberDO member = memberDao.selectByPhone(phone);

        if (scene == SmsSceneEnum.REGISTER && member != null && member.isRegistered()) {
            // 注册场景：手机号已注册 → 409
            throw new ServiceException("该手机号已注册，请直接登录", CONFLICT);
        }
        if (scene == SmsSceneEnum.RESET && (member == null || !member.isRegistered())) {
            // 忘记密码场景：手机号未注册 → 404
            throw new ServiceException("手机号未注册", NOT_FOUND);
        }

        smsCodeService.send(scene.getCode(), phone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        validatePasswordStrength(dto.getPassword());
        // 验证码校验成功才消费（错误／过期返回 409，保留未过期验证码供重试）
        smsCodeService.verifyAndConsume(SmsSceneEnum.REGISTER.getCode(), phone, dto.getSmsCode());
        // 注册成功不签发 token、不刷新最后登录时间
        memberService.registerByPhone(phone, passwordEncoder.encode(dto.getPassword()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        MemberDO member = memberDao.selectByPhone(phone);
        if (member == null || !member.isRegistered()
                || !passwordEncoder.matches(dto.getPassword(), member.getPassword())) {
            throw new ServiceException("手机号或密码错误", CONFLICT);
        }

        // 先刷新最后登录时间，数据库更新成功后才签发登录态
        memberDao.updateLastLoginTime(member.getId(), LocalDateTime.now());
        return memberTokenService.createToken(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        validatePasswordStrength(dto.getPassword());
        MemberDO member = memberDao.selectByPhone(phone);
        if (member == null || !member.isRegistered()) {
            throw new ServiceException("手机号未注册", NOT_FOUND);
        }
        smsCodeService.verifyAndConsume(SmsSceneEnum.RESET.getCode(), phone, dto.getSmsCode());
        memberDao.updatePassword(member.getId(), passwordEncoder.encode(dto.getPassword()));
    }

    /** 密码强度兜底校验（接口层 @Valid 已拦一层） */
    private void validatePasswordStrength(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new ServiceException("密码需 8～20 位且同时包含字母与数字", PARAM_INVALID);
        }
    }

    /** 手机号规整与兜底校验：接口层已校验格式，这里防住 service 直调（null／空白 → 400） */
    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new ServiceException("手机号不能为空", PARAM_INVALID);
        }
        return phone.trim();
    }
}
