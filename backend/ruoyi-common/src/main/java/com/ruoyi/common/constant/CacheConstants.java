package com.ruoyi.common.constant;

/**
 * 缓存的key 常量
 * 
 * @author ruoyi
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 会员（用户端小程序）登录态 redis key
     *
     * <p>与管理端 {@link #LOGIN_TOKEN_KEY} 前缀隔离：会员 token 只在 {@code /api/**} 生效，
     * 管理端 token 只在 {@code /admin/**} 与 {@code sys_*} 生效（用户端接口详细设计 §2.1.2）。</p>
     */
    public static final String MEMBER_TOKEN_KEY = "member_tokens:";

    /**
     * 短信验证码 redis key（后接 {@code <scene>:<phone>}）
     */
    public static final String SMS_CODE_KEY = "sms_codes:";

    /**
     * 短信验证码发送间隔 redis key（后接 {@code <scene>:<phone>}）
     */
    public static final String SMS_CODE_INTERVAL_KEY = "sms_code_interval:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";
}
