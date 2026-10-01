package com.techplant.yoga.coach.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.coach.dao.CoachDao;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.impl.CoachServiceImpl;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.service.ScheduleService;

/**
 * 教练业务实现的单元测试（教练管理详细设计 §2.2、§4）。
 *
 * <p>依赖（教练数据访问、排课门禁统计）全部用模拟对象隔离，<b>不连数据库、不连 Redis</b>。</p>
 *
 * <p><b>覆盖口径：</b>相册（null / 空 / 5 张 / 6 张）与 JSON 序列化、<b>姓名允许重名</b>（无查重调用）、
 * 详情 gallery 反序列化与空数组兜底、修改全量覆盖、<b>删除门禁四分支</b>（404 / 排课数 / 物理删除 /
 * 统计异常 fail-closed）、列表列裁剪、跨模块摘要去重。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CoachServiceImpl 单元测试")
class CoachServiceImplTest
{
    /** 用例里的教练编号 */
    private static final Long COACH_ID = 1001L;

    /** 另一个教练编号 */
    private static final Long OTHER_COACH_ID = 1002L;

    /** 用例里的操作人编号 */
    private static final Long OPERATOR_ID = 1L;

    /** 相册上限（BR-教练-004） */
    private static final int MAX_GALLERY_SIZE = 5;

    @Mock
    private CoachDao coachDao;

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private CoachServiceImpl coachService;

    @BeforeEach
    void setUpOperator()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    @AfterEach
    void clearOperator()
    {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 新增：相册（序列化只发生在 service 层）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：2 个相册 URL → DO.gallery 是 JSON 数组字符串（不是 List）")
    void create_withTwoGalleryUrls_shouldStoreJsonString()
    {
        stubInsertBackfillsId();
        CoachCreateDTO dto = createDTO(" 林教练 ");
        dto.setGallery(Arrays.asList("https://cdn.example.com/coach/a.jpg", "https://cdn.example.com/coach/b.jpg"));

        coachService.create(dto);

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao).insert(captor.capture());
        CoachDO saved = captor.getValue();
        assertEquals("[\"https://cdn.example.com/coach/a.jpg\",\"https://cdn.example.com/coach/b.jpg\"]",
                saved.getGallery());
        assertEquals("林教练", saved.getName(), "请求字段前后空格由转换层去掉");
    }

    @Test
    @DisplayName("新增：相册为 null → 存 NULL（不写 \"[]\"）")
    void create_withNullGallery_shouldStoreNull()
    {
        stubInsertBackfillsId();
        CoachCreateDTO dto = createDTO("林教练");
        dto.setGallery(null);

        coachService.create(dto);

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao).insert(captor.capture());
        assertNull(captor.getValue().getGallery());
    }

    @Test
    @DisplayName("新增：相册为空列表 → 存 NULL")
    void create_withEmptyGallery_shouldStoreNull()
    {
        stubInsertBackfillsId();
        CoachCreateDTO dto = createDTO("林教练");
        dto.setGallery(new ArrayList<String>());

        coachService.create(dto);

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao).insert(captor.capture());
        assertNull(captor.getValue().getGallery());
    }

    @Test
    @DisplayName("新增：相册正好 5 张 → 放行（边界值）")
    void create_withExactlyFiveGalleryUrls_shouldPass()
    {
        stubInsertBackfillsId();
        CoachCreateDTO dto = createDTO("林教练");
        List<String> five = galleryUrls(MAX_GALLERY_SIZE);
        dto.setGallery(five);

        coachService.create(dto);

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao).insert(captor.capture());
        assertTrue(captor.getValue().getGallery().startsWith("[\""));
        assertEquals(MAX_GALLERY_SIZE, parseJsonArray(captor.getValue().getGallery()).size());
    }

    @Test
    @DisplayName("新增：相册 6 张 → 409「相册最多 5 张」，且不落库")
    void create_withSixGalleryUrls_shouldReject409AndNotInsert()
    {
        CoachCreateDTO dto = createDTO("林教练");
        dto.setGallery(galleryUrls(MAX_GALLERY_SIZE + 1));

        ServiceException exception = assertThrows(ServiceException.class, () -> coachService.create(dto));

        assertEquals(409, exception.getCode().intValue());
        assertTrue(exception.getMessage().contains("相册最多 5 张"), "实际文案：" + exception.getMessage());
        verify(coachDao, never()).insert(any(CoachDO.class));
    }

    @Test
    @DisplayName("新增：落库未生效（影响行数 0）→ 500「新增教练失败」")
    void create_whenInsertNotEffective_shouldThrow()
    {
        when(coachDao.insert(any(CoachDO.class))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> coachService.create(createDTO("林教练")));

        assertEquals(500, exception.getCode().intValue());
        assertEquals("新增教练失败", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 姓名允许重名：不做任何查重调用
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重名放行：连续新增两个同名教练都成功，且 DAO 上除 insert 外没有任何交互（无查重查询）")
    void create_withDuplicateName_shouldBeAllowedWithoutAnyDuplicateCheck()
    {
        stubInsertBackfillsId();

        coachService.create(createDTO("林教练"));
        coachService.create(createDTO("林教练"));

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao, times(2)).insert(captor.capture());
        assertEquals("林教练", captor.getAllValues().get(0).getName());
        assertEquals("林教练", captor.getAllValues().get(1).getName());
        // insert 之外不允许有任何交互 —— 一旦有人加回「按名字查重」，这条会红
        verifyNoMoreInteractions(coachDao);
    }

    // ------------------------------------------------------------------
    // 详情：JSON → List<String>，空相册返回空数组
    // ------------------------------------------------------------------

    @Test
    @DisplayName("详情：DO 的 gallery 是 JSON 字符串 → VO 的 gallery 是 List<String>")
    void getById_shouldDeserializeGalleryIntoList()
    {
        CoachDO coach = coach(COACH_ID);
        coach.setGallery("[\"https://cdn.example.com/coach/a.jpg\",\"https://cdn.example.com/coach/b.jpg\"]");
        when(coachDao.selectById(COACH_ID)).thenReturn(coach);

        CoachDetailVO detail = coachService.getById(COACH_ID);

        assertEquals(COACH_ID, detail.getId());
        assertEquals("林教练", detail.getName());
        assertEquals("十年哈他瑜伽经验", detail.getIntro());
        assertEquals("13800000001", detail.getPhone());
        assertEquals(Arrays.asList("https://cdn.example.com/coach/a.jpg", "https://cdn.example.com/coach/b.jpg"),
                detail.getGallery());
    }

    @Test
    @DisplayName("详情：gallery 为 NULL → 返回空数组而不是 null（前端 v-for 不炸）")
    void getById_withNullGallery_shouldReturnEmptyList()
    {
        CoachDO coach = coach(COACH_ID);
        coach.setGallery(null);
        when(coachDao.selectById(COACH_ID)).thenReturn(coach);

        CoachDetailVO detail = coachService.getById(COACH_ID);

        assertNotNull(detail.getGallery(), "空相册必须是空数组，不能是 null");
        assertTrue(detail.getGallery().isEmpty());
    }

    @Test
    @DisplayName("详情：教练不存在 → 404「教练不存在或已被删除」")
    void getById_notExists_shouldThrow404()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> coachService.getById(COACH_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教练不存在或已被删除", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 修改：全量编辑、不做引用检查
    // ------------------------------------------------------------------

    @Test
    @DisplayName("修改：教练不存在 → 404，不更新、不做任何门禁统计")
    void update_notExists_shouldThrow404WithoutUpdate()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> coachService.update(COACH_ID, updateDTO("林教练")));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教练不存在或已被删除", exception.getMessage());
        verify(coachDao, never()).updateById(any(CoachDO.class));
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("修改：相册 6 张 → 409，且不更新")
    void update_withSixGalleryUrls_shouldReject409()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID));
        CoachUpdateDTO dto = updateDTO("林教练");
        dto.setGallery(galleryUrls(MAX_GALLERY_SIZE + 1));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> coachService.update(COACH_ID, dto));

        assertEquals(409, exception.getCode().intValue());
        assertTrue(exception.getMessage().contains("相册最多 5 张"));
        verify(coachDao, never()).updateById(any(CoachDO.class));
    }

    @Test
    @DisplayName("修改：全量覆盖并返回最新详情；已被排课引用也照样能改（不做引用检查）")
    void update_shouldOverwriteAndReturnLatestDetail()
    {
        CoachDO existing = coach(COACH_ID);
        CoachDO latest = coach(COACH_ID);
        latest.setName("周教练");
        latest.setPhone("13800000009");
        latest.setGallery("[\"https://cdn.example.com/coach/new.jpg\"]");
        when(coachDao.selectById(COACH_ID)).thenReturn(existing, latest);
        when(coachDao.updateById(any(CoachDO.class))).thenReturn(1);

        CoachUpdateDTO dto = updateDTO(" 周教练 ");
        dto.setPhone("13800000009");
        dto.setGallery(Collections.singletonList("https://cdn.example.com/coach/new.jpg"));

        CoachDetailVO detail = coachService.update(COACH_ID, dto);

        assertEquals("周教练", detail.getName());
        assertEquals("13800000009", detail.getPhone());
        assertEquals(Collections.singletonList("https://cdn.example.com/coach/new.jpg"), detail.getGallery());

        ArgumentCaptor<CoachDO> captor = ArgumentCaptor.forClass(CoachDO.class);
        verify(coachDao).updateById(captor.capture());
        CoachDO written = captor.getValue();
        assertEquals("周教练", written.getName(), "请求字段前后空格必须去掉");
        assertEquals("[\"https://cdn.example.com/coach/new.jpg\"]", written.getGallery());
        assertEquals(OPERATOR_ID, written.getUpdateBy(), "更新人取自当前登录态");
        // 修改教练不做任何引用检查（详细设计 §2.2.4）
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("修改：更新后重新查询仍不存在 → 404")
    void update_whenRowVanishedAfterUpdate_shouldThrow404()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID), null);
        when(coachDao.updateById(any(CoachDO.class))).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> coachService.update(COACH_ID, updateDTO("林教练")));

        assertEquals(404, exception.getCode().intValue());
    }

    // ------------------------------------------------------------------
    // 删除门禁四分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除①：教练不存在 → 404，不统计、不删除")
    void delete_notExists_shouldThrow404()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> coachService.delete(COACH_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教练不存在或已被删除", exception.getMessage());
        verify(coachDao, never()).deleteById(anyLong());
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("删除②：仍有 3 节未结束排课 → 409「该教练仍有 3 节未结束的排课，无法删除」，不删除")
    void delete_withActiveSchedules_shouldRejectWithCount()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID));
        when(scheduleService.countActiveByCoachId(COACH_ID)).thenReturn(3L);

        ServiceException exception = assertThrows(ServiceException.class, () -> coachService.delete(COACH_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("该教练仍有 3 节未结束的排课，无法删除", exception.getMessage());
        verify(scheduleService).countActiveByCoachId(COACH_ID);
        verify(coachDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除③：无未结束排课 → 物理删除该行")
    void delete_withoutReferences_shouldPhysicallyDelete()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID));
        when(scheduleService.countActiveByCoachId(COACH_ID)).thenReturn(0L);
        when(coachDao.deleteById(COACH_ID)).thenReturn(1);

        coachService.delete(COACH_ID);

        verify(coachDao).deleteById(COACH_ID);
    }

    @Test
    @DisplayName("删除④：引用统计抛异常 → fail-closed，409「引用检查未完成，已拒绝本次删除」，不降级放行")
    void delete_whenReferenceCheckFails_shouldFailClosed()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID));
        when(scheduleService.countActiveByCoachId(COACH_ID)).thenThrow(new IllegalStateException("统计服务不可用"));

        ServiceException exception = assertThrows(ServiceException.class, () -> coachService.delete(COACH_ID));

        assertEquals(409, exception.getCode().intValue());
        assertEquals("引用检查未完成，已拒绝本次删除", exception.getMessage());
        verify(coachDao, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------------
    // 列表：查询条件透传 + 列表项不含大字段
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表：查询条件原样传给 Dao，行映射为列表项（不含 intro/gallery）")
    void page_shouldPassQueryToDaoAndMapRows()
    {
        CoachQuery query = new CoachQuery();
        query.setName("林");
        query.setPageNum(1);
        query.setPageSize(10);

        CoachDO coach = coach(COACH_ID);
        coach.setIntro("不该出现在列表里");
        coach.setGallery("[\"https://cdn.example.com/coach/a.jpg\"]");
        coach.setCreateTime(LocalDateTime.of(2026, 9, 28, 10, 0, 0));
        coach.setUpdateTime(LocalDateTime.of(2026, 9, 28, 10, 5, 0));
        Page<CoachDO> page = new Page<CoachDO>(1, 10);
        page.setTotal(1L);
        page.setRecords(Collections.singletonList(coach));
        when(coachDao.selectAdminPage(query)).thenReturn(page);

        PageResult<CoachListItemVO> result = coachService.page(query);

        assertEquals(Long.valueOf(1L), result.getTotal());
        assertEquals(1, result.getList().size());
        CoachListItemVO row = result.getList().get(0);
        assertEquals(COACH_ID, row.getId());
        assertEquals("林教练", row.getName());
        assertEquals("https://cdn.example.com/coach/avatar.jpg", row.getAvatarUrl());
        assertEquals("13800000001", row.getPhone());
        assertEquals(LocalDateTime.of(2026, 9, 28, 10, 0, 0), row.getCreateTime());
        assertEquals(LocalDateTime.of(2026, 9, 28, 10, 5, 0), row.getUpdateTime());
        verify(coachDao).selectAdminPage(query);

        // 列表项类型上就不允许有大字段（详细设计 §2.2.1、§4.1）
        assertThrows(NoSuchFieldException.class, () -> CoachListItemVO.class.getDeclaredField("intro"));
        assertThrows(NoSuchFieldException.class, () -> CoachListItemVO.class.getDeclaredField("gallery"));
    }

    // ------------------------------------------------------------------
    // 跨模块摘要（排课模块依赖）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("摘要：入参为空／全为 null → 返回空列表，不查库")
    void summaries_withEmptyIds_shouldReturnEmptyList()
    {
        assertTrue(coachService.summaries(null).isEmpty());
        assertTrue(coachService.summaries(Collections.<Long>emptyList()).isEmpty());
        assertTrue(coachService.summaries(Arrays.asList(null, null)).isEmpty());
        verifyNoInteractions(coachDao);
    }

    @Test
    @DisplayName("摘要：ID 去重去 null 后批量查询，返回 id/name/avatarUrl/intro")
    @SuppressWarnings("unchecked")
    void summaries_shouldDeduplicateAndMapSummaryFields()
    {
        when(coachDao.selectSummaryByIds(anyCollection())).thenReturn(
                Arrays.asList(coach(COACH_ID), coach(OTHER_COACH_ID)));

        List<CoachSummaryVO> summaries = coachService.summaries(
                Arrays.asList(COACH_ID, COACH_ID, OTHER_COACH_ID, null));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(coachDao).selectSummaryByIds(captor.capture());
        assertEquals(2, captor.getValue().size(), "重复 ID 必须去重后再查库");
        assertTrue(captor.getValue().containsAll(Arrays.asList(COACH_ID, OTHER_COACH_ID)));
        assertFalse(captor.getValue().contains(null));

        assertEquals(2, summaries.size());
        assertEquals(COACH_ID, summaries.get(0).getId());
        assertEquals("林教练", summaries.get(0).getName());
        assertEquals("https://cdn.example.com/coach/avatar.jpg", summaries.get(0).getAvatarUrl());
        assertEquals("十年哈他瑜伽经验", summaries.get(0).getIntro());
    }

    @Test
    @DisplayName("getSummary：存在 → 返回摘要；不存在 → 404（排课校验教练存在用）")
    void getSummary_shouldReturnSummaryOrThrow404()
    {
        when(coachDao.selectById(COACH_ID)).thenReturn(coach(COACH_ID));

        CoachSummaryVO summary = coachService.getSummary(COACH_ID);

        assertEquals(COACH_ID, summary.getId());
        assertEquals("林教练", summary.getName());

        when(coachDao.selectById(OTHER_COACH_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> coachService.getSummary(OTHER_COACH_ID));

        assertEquals(404, exception.getCode().intValue());
        assertEquals("教练不存在或已被删除", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 模拟 MyBatis-Plus 的 IdType.ASSIGN_ID：insert 时回填雪花主键 */
    private void stubInsertBackfillsId()
    {
        when(coachDao.insert(any(CoachDO.class))).thenAnswer(invocation -> {
            CoachDO toInsert = invocation.getArgument(0);
            toInsert.setId(COACH_ID);
            return 1;
        });
    }

    private CoachDO coach(Long id)
    {
        CoachDO coach = new CoachDO();
        coach.setId(id);
        coach.setName("林教练");
        coach.setAvatarUrl("https://cdn.example.com/coach/avatar.jpg");
        coach.setIntro("十年哈他瑜伽经验");
        coach.setPhone("13800000001");
        return coach;
    }

    private CoachCreateDTO createDTO(String name)
    {
        CoachCreateDTO dto = new CoachCreateDTO();
        dto.setName(name);
        dto.setAvatarUrl("https://cdn.example.com/coach/avatar.jpg");
        dto.setIntro("十年哈他瑜伽经验");
        dto.setPhone("13800000001");
        return dto;
    }

    private CoachUpdateDTO updateDTO(String name)
    {
        CoachUpdateDTO dto = new CoachUpdateDTO();
        dto.setName(name);
        dto.setAvatarUrl("https://cdn.example.com/coach/avatar.jpg");
        dto.setIntro("十年哈他瑜伽经验");
        dto.setPhone("13800000001");
        return dto;
    }

    private List<String> galleryUrls(int size)
    {
        List<String> urls = new ArrayList<String>();
        for (int i = 1; i <= size; i++)
        {
            urls.add("https://cdn.example.com/coach/album-" + i + ".jpg");
        }
        return urls;
    }

    /** 只用于边界值断言：把 JSON 数组解析回列表（测试里不依赖 service 的私有方法） */
    private List<String> parseJsonArray(String json)
    {
        List<String> values = new ArrayList<String>();
        String content = json.trim();
        content = content.substring(1, content.length() - 1);
        if (content.isEmpty())
        {
            return values;
        }
        for (String item : content.split(","))
        {
            values.add(item.trim().replace("\"", ""));
        }
        return values;
    }
}
