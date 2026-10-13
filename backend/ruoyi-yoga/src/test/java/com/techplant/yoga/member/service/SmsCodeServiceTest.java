package com.techplant.yoga.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.exception.ServiceException;

/**
 * 短信验证码的单元测试（用户端接口详细设计 §4.3、§4.4；2026-10-13 复审要求补并发用例）。
 *
 * <p><b>怎么在没有 Redis 的情况下测并发</b>：把 {@link StringRedisTemplate} 换成
 * 一个用 {@link ConcurrentHashMap} 实现的测试替身，它的两个关键语义与 Redis 对齐 —
 * ①{@code SET NX EX} = {@code putIfAbsent}（原子抢锁）；
 * ②Lua 脚本执行 = {@code synchronized} 块内的「读取→比较→删除」（Redis 单线程执行脚本的等价物）。
 * 这样测的是本类在并发下的真实行为：并发发码只能一个成功、并发消费只能一个成功。</p>
 */
@DisplayName("SmsCodeService 单元测试（含并发）")
class SmsCodeServiceTest
{
    private static final String PHONE = "13800138000";

    private static final String REGISTER = "register";

    private static final String RESET = "reset";

    private static final String FIXED_CODE = "888888";

    /** 模拟 Redis：key → 值。用并发 Map + 原子 putIfAbsent 表达 SET NX EX */
    private final Map<String, String> redis = new ConcurrentHashMap<String, String>();

    private StringRedisTemplate stringRedisTemplate;

    private SmsSender smsSender;

    private SmsCodeService smsCodeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        redis.clear();
        stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // SET NX EX
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> redis.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        // SET EX（覆盖写）：set 返回 void，只能用 doAnswer 打桩
        doAnswer(invocation ->
        {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        // Lua：GET → 比较 → （匹配才）DEL，整段互斥，等价 Redis 原子执行脚本
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenAnswer(invocation ->
        {
            List<String> keys = invocation.getArgument(1);
            Object candidate = invocation.getArgument(2);
            String key = keys.get(0);
            synchronized (redis)
            {
                String saved = redis.get(key);
                if (saved == null)
                {
                    return 0L;
                }
                if (!saved.equals(candidate))
                {
                    return -1L;
                }
                redis.remove(key);
                return 1L;
            }
        });

        smsSender = mock(SmsSender.class);
        smsCodeService = new SmsCodeService(stringRedisTemplate, smsSender);
        ReflectionTestUtils.setField(smsCodeService, "expireSeconds", 300);
        ReflectionTestUtils.setField(smsCodeService, "intervalSeconds", 60);
        ReflectionTestUtils.setField(smsCodeService, "fixedCode", FIXED_CODE);
    }

    // ------------------------------------------------------------------ 发码

    @Test
    @DisplayName("发码：写入验证码并占用 60 秒发送间隔")
    void send_shouldStoreCodeAndOccupyInterval()
    {
        smsCodeService.send(RESET, PHONE);

        assertEquals(FIXED_CODE, redis.get(CacheConstants.SMS_CODE_KEY + RESET + ":" + PHONE));
        assertEquals("1", redis.get(CacheConstants.SMS_CODE_INTERVAL_KEY + RESET + ":" + PHONE));
        verify(smsSender).send(PHONE, FIXED_CODE, RESET);
    }

    @Test
    @DisplayName("发码：间隔内重复发送 → 409，且不再发短信")
    void send_withinInterval_shouldConflict()
    {
        smsCodeService.send(RESET, PHONE);

        ServiceException e = assertThrows(ServiceException.class, () -> smsCodeService.send(RESET, PHONE));

        assertEquals(409, e.getCode().intValue());
        assertEquals("验证码发送过于频繁，请稍后再试", e.getMessage());
        verify(smsSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("发码：不同场景各自独立限流（register 不挡 reset）")
    void send_differentScenes_shouldNotBlockEachOther()
    {
        smsCodeService.send(REGISTER, PHONE);

        smsCodeService.send(RESET, PHONE);

        verify(smsSender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("并发发码：同手机号同场景 8 个请求只有一个成功（SET NX EX 抢锁）")
    void send_concurrently_onlyOneSucceeds() throws Exception
    {
        int threads = 8;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        runConcurrently(threads, () ->
        {
            try
            {
                smsCodeService.send(RESET, PHONE);
                success.incrementAndGet();
            }
            catch (ServiceException e)
            {
                assertEquals(409, e.getCode().intValue());
                conflict.incrementAndGet();
            }
        });

        assertEquals(1, success.get(), "并发发码只应有一个成功");
        assertEquals(threads - 1, conflict.get(), "其余都应收到 409");
        verify(smsSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("发码：未配置固定码时发随机 6 位数字")
    void send_withoutFixedCode_shouldUseRandomSixDigits()
    {
        ReflectionTestUtils.setField(smsCodeService, "fixedCode", "");

        smsCodeService.send(RESET, PHONE);

        String stored = redis.get(CacheConstants.SMS_CODE_KEY + RESET + ":" + PHONE);
        assertNotNull(stored);
        assertTrue(stored.matches("\\d{6}"), "应为 6 位数字：" + stored);
        verify(smsSender).send(PHONE, stored, RESET);
    }

    // ------------------------------------------------------------------ 校验与消费

    @Test
    @DisplayName("校验：正确验证码消费一次；再校验同一个码 → 409 已过期")
    void verifyAndConsume_shouldConsumeOnlyOnce()
    {
        smsCodeService.send(RESET, PHONE);

        smsCodeService.verifyAndConsume(RESET, PHONE, FIXED_CODE);

        ServiceException e = assertThrows(ServiceException.class,
                () -> smsCodeService.verifyAndConsume(RESET, PHONE, FIXED_CODE));
        assertEquals(409, e.getCode().intValue());
        assertEquals("验证码已过期，请重新获取", e.getMessage());
    }

    @Test
    @DisplayName("校验：错误验证码 409「验证码错误」，且验证码保留可重试")
    void verifyAndConsume_withWrongCode_shouldKeepCodeForRetry()
    {
        smsCodeService.send(RESET, PHONE);

        ServiceException e = assertThrows(ServiceException.class,
                () -> smsCodeService.verifyAndConsume(RESET, PHONE, "000000"));
        assertEquals("验证码错误", e.getMessage());

        // 保留：正确验证码仍可消费
        smsCodeService.verifyAndConsume(RESET, PHONE, FIXED_CODE);
    }

    @Test
    @DisplayName("校验：未发送或已过期 → 409「验证码已过期，请重新获取」")
    void verifyAndConsume_withoutCode_shouldReportExpired()
    {
        ServiceException e = assertThrows(ServiceException.class,
                () -> smsCodeService.verifyAndConsume(RESET, PHONE, FIXED_CODE));

        assertEquals(409, e.getCode().intValue());
        assertEquals("验证码已过期，请重新获取", e.getMessage());
    }

    @Test
    @DisplayName("校验：旧码请求不能删掉刚写入的新码（Lua 只在值相等时 DEL）")
    void verifyAndConsume_withOldCode_shouldNotDeleteNewCode()
    {
        // 模拟：新码已经写进 Redis，旧码请求随后到达
        String key = CacheConstants.SMS_CODE_KEY + RESET + ":" + PHONE;
        redis.put(key, "222222");

        ServiceException e = assertThrows(ServiceException.class,
                () -> smsCodeService.verifyAndConsume(RESET, PHONE, "111111"));

        assertEquals("验证码错误", e.getMessage());
        assertEquals("222222", redis.get(key), "新码必须还在");

        // 新码仍可正常消费
        smsCodeService.verifyAndConsume(RESET, PHONE, "222222");
        assertTrue(!redis.containsKey(key));
    }

    @Test
    @DisplayName("校验：code 为 null 按错误处理（不误判成功）")
    void verifyAndConsume_withNullCode_shouldFail()
    {
        smsCodeService.send(RESET, PHONE);

        ServiceException e = assertThrows(ServiceException.class,
                () -> smsCodeService.verifyAndConsume(RESET, PHONE, null));

        assertEquals(409, e.getCode().intValue());
        assertEquals("验证码错误", e.getMessage());
    }

    @Test
    @DisplayName("并发消费：同一个验证码 8 个请求同时提交，只有一个成功")
    void verifyAndConsume_concurrently_onlyOneSucceeds() throws Exception
    {
        smsCodeService.send(RESET, PHONE);
        int threads = 8;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        runConcurrently(threads, () ->
        {
            try
            {
                smsCodeService.verifyAndConsume(RESET, PHONE, FIXED_CODE);
                success.incrementAndGet();
            }
            catch (ServiceException e)
            {
                assertEquals(409, e.getCode().intValue());
                conflict.incrementAndGet();
            }
        });

        assertEquals(1, success.get(), "同一个验证码只能被消费一次");
        assertEquals(threads - 1, conflict.get(), "其余请求应收到 409（已过期）");
    }

    @Test
    @DisplayName("场景校验：非法场景 → 500（service 直调兜底）")
    void requireValidScene_withInvalidScene_shouldFail()
    {
        ServiceException e = assertThrows(ServiceException.class, () -> SmsCodeService.requireValidScene("login"));

        assertEquals(500, e.getCode().intValue());
        assertEquals("验证码场景取值为 register／reset", e.getMessage());
    }

    // ------------------------------------------------------------------ 夹具

    /** 让 N 个线程尽量同时进入被测方法 */
    private void runConcurrently(int threads, Runnable task) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try
        {
            for (int i = 0; i < threads; i++)
            {
                futures.add(pool.submit(() ->
                {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        finally
        {
            pool.shutdownNow();
        }
    }
}
