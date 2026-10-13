package com.techplant.yoga.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;

/**
 * 会员基础能力的单元测试（会员管理详细设计 §4.6、用户端接口详细设计 §4.4）。
 *
 * <p>锁三条口径：①未注册档案只补密码、保留会员 ID 与资料；②已注册／并发冲突一律 409；
 * ③无档案时新建 V0 会员（显示名 = 忻会员 + 后 4 位、phone_last4 落库）。</p>
 */
@DisplayName("MemberServiceImpl 单元测试")
class MemberServiceImplTest
{
    private static final String PHONE = "13800138000";

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    private MemberDao memberDao;

    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp()
    {
        memberDao = mock(MemberDao.class);
        memberService = new MemberServiceImpl(memberDao);
    }

    @Test
    @DisplayName("注册：无档案 → 新建 V0 会员，显示名/后四位/性别/等级按规则落库")
    void registerByPhone_withoutProfile_shouldCreateV0Member()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);
        when(memberDao.insert(any(MemberDO.class))).thenReturn(1);

        memberService.registerByPhone(PHONE, HASH);

        ArgumentCaptor<MemberDO> captor = ArgumentCaptor.forClass(MemberDO.class);
        verify(memberDao).insert(captor.capture());
        MemberDO inserted = captor.getValue();
        assertEquals(PHONE, inserted.getPhone());
        assertEquals(HASH, inserted.getPassword());
        assertEquals("8000", inserted.getPhoneLast4());
        assertEquals("忻会员8000", inserted.getName());
        assertEquals(Integer.valueOf(0), inserted.getGender());
        assertEquals(Integer.valueOf(0), inserted.getLevel());
        assertTrue(inserted.isRegistered());
        verify(memberDao, never()).updatePasswordIfUnregistered(any(), any());
    }

    @Test
    @DisplayName("注册：已有未注册档案 → 只补密码，保留原会员 ID 与资料")
    void registerByPhone_withUnregisteredProfile_shouldOnlyFillPassword()
    {
        MemberDO existing = new MemberDO();
        existing.setId(1856739201475235902L);
        existing.setPhone(PHONE);
        existing.setPhoneLast4("8000");
        existing.setName("忻会员8000");
        existing.setGender(1);
        existing.setLevel(0);
        when(memberDao.selectByPhone(PHONE)).thenReturn(existing);
        when(memberDao.updatePasswordIfUnregistered(existing.getId(), HASH)).thenReturn(1);

        memberService.registerByPhone(PHONE, HASH);

        verify(memberDao).updatePasswordIfUnregistered(existing.getId(), HASH);
        verify(memberDao, never()).insert(any(MemberDO.class));
    }

    @Test
    @DisplayName("注册：已有未注册档案但并发被别人补上（影响行数 0）→ 409")
    void registerByPhone_whenConcurrentFillWins_shouldConflict()
    {
        MemberDO existing = new MemberDO();
        existing.setId(1L);
        existing.setPhone(PHONE);
        when(memberDao.selectByPhone(PHONE)).thenReturn(existing);
        when(memberDao.updatePasswordIfUnregistered(1L, HASH)).thenReturn(0);

        ServiceException e = assertThrows(ServiceException.class, () -> memberService.registerByPhone(PHONE, HASH));

        assertEquals(409, e.getCode().intValue());
        assertEquals("该手机号已注册", e.getMessage());
    }

    @Test
    @DisplayName("注册：手机号已注册 → 409，不写库")
    void registerByPhone_withRegisteredPhone_shouldConflict()
    {
        MemberDO existing = new MemberDO();
        existing.setId(1L);
        existing.setPhone(PHONE);
        existing.setPassword(HASH);
        when(memberDao.selectByPhone(PHONE)).thenReturn(existing);

        ServiceException e = assertThrows(ServiceException.class, () -> memberService.registerByPhone(PHONE, HASH));

        assertEquals(409, e.getCode().intValue());
        verify(memberDao, never()).insert(any(MemberDO.class));
        verify(memberDao, never()).updatePasswordIfUnregistered(any(), any());
    }

    @Test
    @DisplayName("注册：唯一键并发冲突（DuplicateKeyException）→ 409，不覆盖并发建档结果")
    void registerByPhone_whenDuplicateKey_shouldConflict()
    {
        when(memberDao.selectByPhone(PHONE)).thenReturn(null);
        when(memberDao.insert(any(MemberDO.class))).thenThrow(new DuplicateKeyException("uk_member_phone"));

        ServiceException e = assertThrows(ServiceException.class, () -> memberService.registerByPhone(PHONE, HASH));

        assertEquals(409, e.getCode().intValue());
        assertEquals("该手机号已注册", e.getMessage());
        assertNotNull(e.getCause());
    }

    @Test
    @DisplayName("注册：手机号为空或密码哈希为空 → 500（service 直调的兜底校验）")
    void registerByPhone_withInvalidArguments_shouldFail()
    {
        assertEquals(500, assertThrows(ServiceException.class, () -> memberService.registerByPhone("  ", HASH))
                .getCode().intValue());
        assertEquals(500, assertThrows(ServiceException.class, () -> memberService.registerByPhone(PHONE, " "))
                .getCode().intValue());
    }

    @Test
    @DisplayName("存在性与注册态：委托给 DAO，不自己拼条件")
    void existsAndRegistered_shouldDelegateToDao()
    {
        when(memberDao.selectById(1L)).thenReturn(new MemberDO());
        when(memberDao.existsRegisteredById(2L)).thenReturn(true);
        when(memberDao.existsRegisteredById(3L)).thenReturn(false);

        assertTrue(memberService.existsById(1L));
        assertFalse(memberService.existsById(9L));
        assertTrue(memberService.isRegistered(2L));
        assertFalse(memberService.isRegistered(3L));
    }
}
