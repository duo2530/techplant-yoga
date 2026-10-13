package com.techplant.yoga.member.controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.techplant.yoga.member.dto.LoginDTO;
import com.techplant.yoga.member.dto.RegisterDTO;
import com.techplant.yoga.member.dto.ResetPasswordDTO;
import com.techplant.yoga.member.dto.SmsCodeSendDTO;
import com.techplant.yoga.member.service.AccountService;
import com.techplant.yoga.member.service.MemberTokenService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端账号接口。 */
@Api(tags = "用户端-账号")
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController
{
    private final AccountService accountService;

    private final MemberTokenService memberTokenService;

    public AuthController(AccountService accountService, MemberTokenService memberTokenService)
    {
        this.accountService = accountService;
        this.memberTokenService = memberTokenService;
    }

    /** 发送短信验证码（注册／忘记密码） */
    @ApiOperation("发送短信验证码")
    @PostMapping("/sms-code")
    public AjaxResult sendSmsCode(@Valid @RequestBody SmsCodeSendDTO dto)
    {
        accountService.sendSmsCode(dto);
        return AjaxResult.success("验证码已发送");
    }

    /** 注册：成功不签发 token，前端跳回登录页 */
    @ApiOperation("注册")
    @PostMapping("/register")
    public AjaxResult register(@Valid @RequestBody RegisterDTO dto)
    {
        accountService.register(dto);
        return AjaxResult.success("注册成功，请登录");
    }

    /** 登录：返回 token、过期时间戳、会员 ID 与显示名 */
    @ApiOperation("登录")
    @PostMapping("/login")
    public AjaxResult login(@Valid @RequestBody LoginDTO dto)
    {
        return AjaxResult.success("登录成功", accountService.login(dto));
    }

    /** 忘记密码：覆盖 BCrypt 哈希，不需要旧密码 */
    @ApiOperation("忘记密码")
    @PostMapping("/reset-password")
    public AjaxResult resetPassword(@Valid @RequestBody ResetPasswordDTO dto)
    {
        accountService.resetPassword(dto);
        return AjaxResult.success("密码已重置");
    }

    /**
     * 退出登录：撤销当前会员会话，不影响该会员的其他会话
     *
     * <p>幂等：token 缺失、已失效或已删除也返回成功；有效 token 用来定位待清理的会话。</p>
     */
    @ApiOperation("退出登录")
    @PostMapping("/logout")
    public AjaxResult logout(HttpServletRequest request)
    {
        memberTokenService.revokeSession(request);
        return AjaxResult.success("退出成功");
    }
}
