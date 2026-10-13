package com.techplant.yoga.member.service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.member.enums.SmsSceneEnum;

/**
 * 短信验证码（用户端接口详细设计 §4.3）。
 *
 * <p><b>Redis 口径</b>：验证码 key = {@code sms_codes:<scene>:<phone>}（有效期 5 分钟），
 * 发送间隔 key = {@code sms_code_interval:<scene>:<phone>}（60 秒）。
 * 发送新码直接覆盖同一个 key，旧的验证码自然作废；校验失败<b>不消费</b>，
 * 有效期内允许不限次数重试，校验成功才删除（§4.3 第 3 步、§4.4 第 1 步）。</p>
 *
 * <p><b>并发安全（2026-10-13 复审修复）</b>：两处都改成 Redis 原子操作——
 * ①发送间隔用 {@code SET NX EX} 抢锁，并发发码只有一个能成功；
 * ②校验消费用 Lua 脚本把「读取 → 比较 → 删除」做成一个原子步骤，
 * 同一个验证码并发提交只能成功一次，且旧码请求不可能删掉刚写入的新码
 * （脚本只在「取到的值 == 请求值」时才 DEL）。</p>
 *
 * <p><b>为什么用 {@link StringRedisTemplate} 而不是 {@code RedisCache}</b>：这两个能力
 * （NX 抢锁、Lua 比较删除）需要在 Redis 侧按<b>原始字符串</b>比较，而工程里
 * {@code RedisCache} 的值序列化器是带 {@code WriteClassName} 的 fastjson2，
 * 存进去的是 JSON 文本（如 {@code "888888"} 带引号）。验证码是纯数字串、
 * 也没有别的消费者，因此这里单独用字符串模板，key 前缀仍与
 * {@link CacheConstants#SMS_CODE_KEY}／{@link CacheConstants#SMS_CODE_INTERVAL_KEY} 保持一致。</p>
 *
 * <p><b>开发固定码</b>：{@code sms.code.fixedCode} 非空时统一用它（默认 8888 的 6 位版本），
 * 便于本机联调；生产置空即走随机 6 位数字。响应与日志都不回显验证码给客户端。</p>
 */
@Service
public class SmsCodeService
{
    /** 验证码过期／错误／发送过频：统一按状态冲突返回 409 */
    private static final int CONFLICT = 409;

    /** 场景不合法：参数校验失败 */
    private static final int PARAM_INVALID = 500;

    /**
     * 校验并消费验证码的 Lua 脚本（原子：读取 → 比较 → 删除）
     *
     * <p>返回：{@code 1} 校验成功并已消费；{@code -1} 验证码错误（<b>保留</b>，允许重试）；
     * {@code 0} 验证码不存在或已过期。</p>
     */
    private static final String VERIFY_AND_CONSUME_LUA =
            "local saved = redis.call('GET', KEYS[1])\n"
            + "if not saved then return 0 end\n"
            + "if saved ~= ARGV[1] then return -1 end\n"
            + "redis.call('DEL', KEYS[1])\n"
            + "return 1";

    private static final RedisScript<Long> VERIFY_AND_CONSUME_SCRIPT =
            new DefaultRedisScript<Long>(VERIFY_AND_CONSUME_LUA, Long.class);

    /** 验证码有效期（秒），默认 5 分钟 */
    @Value("${sms.code.expireSeconds:300}")
    private int expireSeconds;

    /** 同一手机号同一场景的发送间隔（秒），默认 60 秒 */
    @Value("${sms.code.intervalSeconds:60}")
    private int intervalSeconds;

    /** 开发固定验证码；为空则随机生成 */
    @Value("${sms.code.fixedCode:}")
    private String fixedCode;

    private final StringRedisTemplate stringRedisTemplate;

    private final SmsSender smsSender;

    public SmsCodeService(StringRedisTemplate stringRedisTemplate, SmsSender smsSender)
    {
        this.stringRedisTemplate = stringRedisTemplate;
        this.smsSender = smsSender;
    }

    /**
     * 发送验证码：原子抢发送间隔 → 生成并落 Redis（覆盖旧码）→ 交给 {@link SmsSender}
     *
     * <p>{@code SET NX EX} 保证并发发码只有一个成功；落码与限流键都在发送之前完成，
     * 因此短信通道失败时用户需等到间隔结束才能重发（这是防刷的取舍，不是缺陷）。</p>
     *
     * @throws ServiceException 发送过频（409）
     */
    public void send(String scene, String phone)
    {
        String limitKey = intervalKey(scene, phone);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", intervalSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired))
        {
            throw new ServiceException("验证码发送过于频繁，请稍后再试", CONFLICT);
        }

        String code = nextCode();
        // 新码覆盖旧码（同一个 key），旧码自然作废
        stringRedisTemplate.opsForValue().set(codeKey(scene, phone), code, expireSeconds, TimeUnit.SECONDS);
        smsSender.send(phone, code, scene);
    }

    /**
     * 校验并消费验证码（Lua 原子执行）
     *
     * <p>错误／过期返回 409 且<b>保留</b>未过期验证码；校验成功才删除，
     * 并发提交同一个验证码只有一个能成功。</p>
     */
    public void verifyAndConsume(String scene, String phone, String code)
    {
        String candidate = code == null ? "" : code;
        Long result = stringRedisTemplate.execute(VERIFY_AND_CONSUME_SCRIPT,
                Collections.singletonList(codeKey(scene, phone)), candidate);
        if (result == null || result.longValue() == 0L)
        {
            throw new ServiceException("验证码已过期，请重新获取", CONFLICT);
        }
        if (result.longValue() < 0L)
        {
            // 不删除：有效期内允许继续重试（§4.4 第 1 步）
            throw new ServiceException("验证码错误", CONFLICT);
        }
    }

    /** 6 位验证码：配置了固定码就用固定码，否则随机（首位允许 0，按字符串比较） */
    private String nextCode()
    {
        if (fixedCode != null && !fixedCode.trim().isEmpty())
        {
            return fixedCode.trim();
        }
        int value = (int) (Math.random() * 1000000);
        return String.format("%06d", value);
    }

    private String codeKey(String scene, String phone)
    {
        return CacheConstants.SMS_CODE_KEY + scene + ":" + phone;
    }

    private String intervalKey(String scene, String phone)
    {
        return CacheConstants.SMS_CODE_INTERVAL_KEY + scene + ":" + phone;
    }

    /** 场景合法性（接口层已用 @Pattern 校验，这里保证 service 直调也安全） */
    public static void requireValidScene(String scene)
    {
        if (!SmsSceneEnum.isValid(scene))
        {
            throw new ServiceException("验证码场景取值为 " + SmsSceneEnum.codes(), PARAM_INVALID);
        }
    }
}
