package com.techplant.yoga.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.security.MemberLoginUser;
import com.techplant.yoga.member.vo.LoginVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * 会员登录态的单元测试（用户端接口详细设计 §4.5、§4.6、§4.7）。
 *
 * <p>Redis 用 Mockito 隔离，但 JWT 是<b>真签真解</b>：验证「JWT 只带会话标识」、
 * 「Redis key 前缀 member_tokens:」、「过期会话读不到且被清理」、
 * 「退出只删本次会话、token 失效也幂等成功」。</p>
 */
@DisplayName("MemberTokenService 单元测试")
class MemberTokenServiceTest
{
    private static final String SECRET = "abcdefghijklmnopqrstuvwxyz";

    private static final int EXPIRE_MINUTES = 10080;

    private static final String HEADER = "Authorization";

    private static final String SESSION_ID = "session-uuid-0001";

    private RedisCache redisCache;

    private MemberTokenService memberTokenService;

    @BeforeEach
    void setUp()
    {
        redisCache = mock(RedisCache.class);
        memberTokenService = new MemberTokenService(redisCache, HEADER, SECRET, EXPIRE_MINUTES);
    }

    @Test
    @DisplayName("签发：写 member_tokens: 会话（7 天 TTL），返回 token/expireTime/memberId/memberName")
    void createToken_shouldCacheSessionAndReturnLoginVO()
    {
        MemberDO member = member();
        long before = System.currentTimeMillis();

        LoginVO loginVO = memberTokenService.createToken(member);

        assertNotNull(loginVO.getToken());
        assertEquals(member.getId(), loginVO.getMemberId());
        assertEquals(member.getName(), loginVO.getMemberName());
        assertTrue(loginVO.getExpireTime() >= before + EXPIRE_MINUTES * 60L * 1000L);

        ArgumentCaptor<MemberLoginUser> captor = ArgumentCaptor.forClass(MemberLoginUser.class);
        verify(redisCache).setCacheObject(startsWith(CacheConstants.MEMBER_TOKEN_KEY), captor.capture(),
                eq(EXPIRE_MINUTES), eq(TimeUnit.MINUTES));
        MemberLoginUser session = captor.getValue();
        assertEquals(member.getId(), session.getMemberId());
        assertEquals(member.getPhone(), session.getPhone());
        assertEquals(loginVO.getExpireTime(), session.getExpireTime());
        assertNotNull(session.getToken());

        // JWT 里只带随机会话标识，不带会员身份
        Claims claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(loginVO.getToken()).getBody();
        assertEquals(session.getToken(), claims.get(MemberTokenService.MEMBER_LOGIN_USER_KEY));
        assertNull(claims.get("memberId"));
        // JWT 自身也带 exp（与会话过期时间一致，秒级精度）
        assertNotNull(claims.getExpiration());
        assertTrue(Math.abs(claims.getExpiration().getTime() - loginVO.getExpireTime()) <= 1000L,
                "JWT exp 应与 Redis 会话过期时间一致");
    }

    @Test
    @DisplayName("鉴权：有效 token → 取到 Redis 里的会员登录对象")
    void getLoginUser_withValidToken_shouldReturnSession()
    {
        MemberLoginUser session = session(System.currentTimeMillis() + 60_000L);
        when(redisCache.getCacheObject(CacheConstants.MEMBER_TOKEN_KEY + SESSION_ID)).thenReturn(session);

        MemberLoginUser actual = memberTokenService.getLoginUser(requestWithToken(jwt(SESSION_ID)));

        assertNotNull(actual);
        assertEquals(session.getMemberId(), actual.getMemberId());
        verify(redisCache, never()).deleteObject(any(String.class));
    }

    @Test
    @DisplayName("鉴权：会话已过期 → 返回 null 并清掉残留会话")
    void getLoginUser_withExpiredSession_shouldReturnNullAndClean()
    {
        when(redisCache.getCacheObject(CacheConstants.MEMBER_TOKEN_KEY + SESSION_ID))
                .thenReturn(session(System.currentTimeMillis() - 1000L));

        assertNull(memberTokenService.getLoginUser(requestWithToken(jwt(SESSION_ID))));

        verify(redisCache).deleteObject(CacheConstants.MEMBER_TOKEN_KEY + SESSION_ID);
    }

    @Test
    @DisplayName("鉴权：会话不存在（已退出／已删除）→ 返回 null，不报错")
    void getLoginUser_withoutSession_shouldReturnNull()
    {
        when(redisCache.getCacheObject(any(String.class))).thenReturn(null);

        assertNull(memberTokenService.getLoginUser(requestWithToken(jwt(SESSION_ID))));
    }

    @Test
    @DisplayName("鉴权：没有 Authorization 头／token 是乱码 → 返回 null，不碰 Redis")
    void getLoginUser_withoutUsableToken_shouldReturnNull()
    {
        assertNull(memberTokenService.getLoginUser(new MockHttpServletRequest()));
        assertNull(memberTokenService.getLoginUser(requestWithToken("not-a-jwt")));
        verifyNoInteractions(redisCache);
    }

    @Test
    @DisplayName("鉴权：JWT 自身已过期 → 返回 null，不碰 Redis（exp 双保险生效）")
    void getLoginUser_withExpiredJwt_shouldReturnNull()
    {
        String expired = Jwts.builder()
                .setClaims(claims(SESSION_ID))
                .setExpiration(new java.util.Date(System.currentTimeMillis() - 60_000L))
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .compact();

        assertNull(memberTokenService.getLoginUser(requestWithToken(expired)));
        verifyNoInteractions(redisCache);
    }

    @Test
    @DisplayName("鉴权：管理端 token（不同 claim）拿不到会员会话")
    void getLoginUser_withAdminStyleToken_shouldReturnNull()
    {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("login_user_key", SESSION_ID);
        String adminStyleToken = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, SECRET).compact();

        assertNull(memberTokenService.getLoginUser(requestWithToken(adminStyleToken)));
        verifyNoInteractions(redisCache);
    }

    @Test
    @DisplayName("退出：删除本次 token 对应的会话，不影响该会员的其他会话")
    void revokeSession_shouldDeleteOnlyCurrentSession()
    {
        memberTokenService.revokeSession(requestWithToken(jwt(SESSION_ID)));

        verify(redisCache).deleteObject(CacheConstants.MEMBER_TOKEN_KEY + SESSION_ID);
        verify(redisCache, never()).deleteObject(any(java.util.Collection.class));
    }

    @Test
    @DisplayName("退出：token 缺失或已失效 → 幂等成功，不碰 Redis、不抛异常")
    void revokeSession_withoutValidToken_shouldBeIdempotent()
    {
        memberTokenService.revokeSession(new MockHttpServletRequest());
        memberTokenService.revokeSession(requestWithToken("expired-or-broken"));

        verifyNoInteractions(redisCache);
    }

    // ------------------------------------------------------------------ 夹具

    private MemberDO member()
    {
        MemberDO member = new MemberDO();
        member.setId(1856739201475235902L);
        member.setPhone("13800138000");
        member.setName("忻会员8000");
        return member;
    }

    private MemberLoginUser session(long expireTime)
    {
        MemberLoginUser session = new MemberLoginUser();
        session.setToken(SESSION_ID);
        session.setMemberId(1856739201475235902L);
        session.setPhone("13800138000");
        session.setLoginTime(System.currentTimeMillis());
        session.setExpireTime(expireTime);
        session.setIpaddr("127.0.0.1");
        return session;
    }

    private String jwt(String sessionId)
    {
        return Jwts.builder().setClaims(claims(sessionId)).signWith(SignatureAlgorithm.HS512, SECRET).compact();
    }

    private Map<String, Object> claims(String sessionId)
    {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(MemberTokenService.MEMBER_LOGIN_USER_KEY, sessionId);
        return claims;
    }

    private MockHttpServletRequest requestWithToken(String token)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "Bearer " + token);
        return request;
    }
}
