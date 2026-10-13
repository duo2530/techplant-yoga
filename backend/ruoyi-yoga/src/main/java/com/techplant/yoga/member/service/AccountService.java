package com.techplant.yoga.member.service;

import com.techplant.yoga.member.dto.LoginDTO;
import com.techplant.yoga.member.dto.RegisterDTO;
import com.techplant.yoga.member.dto.ResetPasswordDTO;
import com.techplant.yoga.member.dto.SmsCodeSendDTO;
import com.techplant.yoga.member.vo.LoginVO;

/**
 * 用户端账号能力（用户端接口详细设计 §4.3～§4.6）。
 *
 * <p>覆盖发验证码、注册、登录、忘记密码；退出登录不需要业务规则，
 * 由接口层直接调 {@link MemberTokenService#revokeSession} 完成会话清理。</p>
 */
public interface AccountService
{
    /** 发送短信验证码（§2.2.4）：按场景检查已注册／未注册条件与 60 秒发送间隔 */
    void sendSmsCode(SmsCodeSendDTO dto);

    /** 注册（§2.2.5）：校验注册场景验证码与密码强度，成功后不签发 token */
    void register(RegisterDTO dto);

    /** 登录（§2.2.6）：手机号或密码错误统一提示，成功后刷新最后登录时间并签发 7 天登录态 */
    LoginVO login(LoginDTO dto);

    /** 忘记密码（§2.2.7）：仅已注册手机号可重置，覆盖 BCrypt 哈希 */
    void resetPassword(ResetPasswordDTO dto);
}
