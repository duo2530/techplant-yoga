package com.techplant.yoga.member.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.security.MemberLoginUser;
import com.techplant.yoga.member.vo.LoginVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * 会员登录态（用户端接口详细设计 §4.5、§4.6、§4.7）。
 *
 * <p><b>JWT 只带随机会话标识</b>，会员身份与有效期放在 Redis 的
 * {@code member_tokens:<uuid>} 里（{@link MemberLoginUser}）；有效期 7 天、
 * <b>不自动续期</b>（§4.5 第 2 步），与框架 {@code TokenService} 的「快到期自动刷新」不同。</p>
 *
 * <p><b>与管理端隔离</b>：本类只读写 {@code member_tokens:} 前缀，签名密钥沿用
 * {@code token.secret} 但 claim 名不同（{@code member_login_user_key}）；
 * 管理端 {@code TokenService} 找的是 {@code login_tokens:} 前缀，
 * 因此两边 token 互不认账（§2.1.2）。</p>
 */
@Service
public class MemberTokenService
{
    private static final Logger log = LoggerFactory.getLogger(MemberTokenService.class);

    /** 会员会话标识在 JWT 里的 claim 名（与管理端的 login_user_key 显式区分） */
    public static final String MEMBER_LOGIN_USER_KEY = "member_login_user_key";

    private static final long MILLIS_MINUTE = 60L * 1000L;

    private final RedisCache redisCache;

    /** 令牌自定义标识（请求头名） */
    private final String header;

    /** 令牌密钥（与管理端同一份配置） */
    private final String secret;

    /** 会员令牌有效期（分钟），默认 7 天 */
    private final long expireMinutes;

    public MemberTokenService(RedisCache redisCache,
            @Value("${token.header}") String header,
            @Value("${token.secret}") String secret,
            @Value("${member.token.expireTime:10080}") long expireMinutes)
    {
        this.redisCache = redisCache;
        this.header = header;
        this.secret = secret;
        this.expireMinutes = expireMinutes;
    }

    /**
     * 签发会员令牌并写入 Redis 会话（登录成功调用）
     *
     * <p>Redis 写入失败会直接抛出，调用方<b>不得</b>返回成功 token（§4.5 第 3 步）。</p>
     */
    public LoginVO createToken(MemberDO member)
    {
        long now = System.currentTimeMillis();
        long expireTime = now + expireMinutes * MILLIS_MINUTE;
        String uuid = IdUtils.fastUUID();

        MemberLoginUser loginUser = new MemberLoginUser();
        loginUser.setToken(uuid);
        loginUser.setMemberId(member.getId());
        loginUser.setPhone(member.getPhone());
        loginUser.setLoginTime(now);
        loginUser.setExpireTime(expireTime);
        loginUser.setIpaddr(currentIpAddr());
        redisCache.setCacheObject(memberTokenKey(uuid), loginUser, (int) expireMinutes, TimeUnit.MINUTES);

        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(MEMBER_LOGIN_USER_KEY, uuid);
        // 双保险：会话有效期以 Redis 里的 MemberLoginUser.expireTime 为准（§2.6.10），
        // JWT 自身也带 exp，避免「只验签名、不验时间」的 token 被无限期复用
        String token = Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(expireTime))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpireTime(expireTime);
        loginVO.setMemberId(member.getId());
        loginVO.setMemberName(member.getName());
        return loginVO;
    }

    /**
     * 取当前请求的会员登录态
     *
     * @return 无 token／token 非法／会话不存在或已过期一律返回 null（由调用方决定是否 401）
     */
    public MemberLoginUser getLoginUser(HttpServletRequest request)
    {
        String token = getToken(request);
        if (StringUtils.isEmpty(token))
        {
            return null;
        }
        try
        {
            String uuid = parseSessionId(token);
            if (StringUtils.isEmpty(uuid))
            {
                return null;
            }
            MemberLoginUser loginUser = redisCache.getCacheObject(memberTokenKey(uuid));
            if (loginUser == null)
            {
                return null;
            }
            if (loginUser.getExpireTime() != null && loginUser.getExpireTime() < System.currentTimeMillis())
            {
                // 自然到期：顺手清掉残留会话
                redisCache.deleteObject(memberTokenKey(uuid));
                return null;
            }
            return loginUser;
        }
        catch (Exception e)
        {
            // 签名不对、结构不对、Redis 读不到：一律当成未登录，不降级放行
            log.error("获取会员登录态异常'{}'", e.getMessage());
            return null;
        }
    }

    /**
     * 退出登录：删除当前 token 对应的会话，<b>不影响该会员的其他会话</b>（§4.6 第 2 步）。
     *
     * <p>幂等口径：token 缺失、签名不合法或会话已删除都直接返回（视为已退出）；
     * 但 Redis 删除本身失败会向上抛出，由统一异常处理器返回 500（§2.2.8）。</p>
     */
    public void revokeSession(HttpServletRequest request)
    {
        String token = getToken(request);
        if (StringUtils.isEmpty(token))
        {
            return;
        }
        String uuid;
        try
        {
            uuid = parseSessionId(token);
        }
        catch (Exception e)
        {
            // 无效/过期 token：没有会话可清，仍然算退出成功
            log.debug("退出登录：token 已失效，跳过会话清理");
            return;
        }
        if (StringUtils.isEmpty(uuid))
        {
            return;
        }
        redisCache.deleteObject(memberTokenKey(uuid));
    }

    /** 从请求头取会员 token（去掉 Bearer 前缀），没有则返回 null */
    public String getToken(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        String token = request.getHeader(header);
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX))
        {
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return StringUtils.isEmpty(token) ? null : token;
    }

    /** 解析 JWT 取会话标识（不查 Redis） */
    private String parseSessionId(String token)
    {
        Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        return (String) claims.get(MEMBER_LOGIN_USER_KEY);
    }

    /**
     * 当前请求 IP
     *
     * <p>{@code IpUtils.getIpAddr()} 直接取 {@code RequestContextHolder}，非 web 线程
     * （定时任务等）会抛 NPE；这里先判空，取不到就留空，不打断签发。</p>
     */
    private String currentIpAddr()
    {
        if (RequestContextHolder.getRequestAttributes() == null)
        {
            return null;
        }
        return IpUtils.getIpAddr();
    }

    private String memberTokenKey(String uuid)
    {
        return CacheConstants.MEMBER_TOKEN_KEY + uuid;
    }
}
