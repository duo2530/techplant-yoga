package com.techplant.yoga.membercard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.member.dao.MemberDaoImpl;
import com.techplant.yoga.member.dao.MemberLevelDictDaoImpl;
import com.techplant.yoga.member.service.impl.MemberServiceImpl;
import com.techplant.yoga.membercard.dao.MemberCardDao;
import com.techplant.yoga.membercard.dao.MemberCardDaoImpl;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.query.MemberCardQuery;
import com.techplant.yoga.membercard.service.impl.MemberCardServiceImpl;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;

/**
 * 会员卡管理业务的集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。
 *
 * <p>会员与门店都走真实的跨模块 Service，不模拟内部调用；会员、门店与计数器夹具由用例自行准备。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberCardDaoImpl.class, MemberDaoImpl.class,
    MemberLevelDictDaoImpl.class, MemberServiceImpl.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class, MemberCardServiceImpl.class, CardNoGenerator.class})
@DisplayName("MemberCardService 集成测试（Testcontainers MySQL：发卡 / 卡号 / 有效期 / 状态流转 / 关联兜底）")
class MemberCardServiceImplTest extends AbstractYogaIntegrationTest {
    private static final Long MEMBER_ID = 1856739201475235902L;

    private static final Long UNKNOWN_ID = 999999L;

    /** 开卡门店：徐汇店 */
    private static final Long OPEN_STORE_ID = 1856739201475235901L;

    /** 适用门店：浦东店 */
    private static final Long STORE_ID = 1856739201475235903L;

    private static final String OPEN_STORE_NO = "0001";

    private static final String REGION_CODE = "310104";

    @Autowired
    private MemberCardService memberCardService;

    @Autowired
    private MemberCardDao memberCardDao;

    @Autowired
    private CardNoGenerator cardNoGenerator;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_member_card", "t_member", "t_store", "t_store_no_counter", "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                + " list_class, is_default, status, create_by, create_time) VALUES (1, '徐汇区', ?, 'store_region',"
                + " '', 'default', 'N', '0', 'admin', NOW())", REGION_CODE);
        jdbcTemplate.update("INSERT INTO t_store_no_counter (id, last_no) VALUES (1, 3)");
        insertMember();
        insertStore(OPEN_STORE_ID, OPEN_STORE_NO, "徐汇店");
        insertStore(STORE_ID, "0003", "浦东店");
    }

    @Test
    @DisplayName("发卡：次数卡生成 18 位卡号（日期＋门店编号＋时间），剩余次数为购买与赠送之和，未写有效期")
    void create_timesCard_shouldGenerateCardNoAndInitialTimes() {
        memberCardService.create(timesCardDTO());

        MemberCardDO card = latestCard();
        assertNotNull(card.getId());
        assertEquals(OPERATOR_ID, card.getCreateBy());
        assertCardNo(card.getCardNo());
        assertEquals(Integer.valueOf(12), card.getRemainingTimes(), "剩余次数 = 购买 10 + 赠送 2");
        assertEquals(Integer.valueOf(12), card.getValidPeriodCount());
        assertNull(card.getValidPeriodUnit(), "有效期单位在开卡时才写");
        assertNull(card.getValidStartTime());
        assertNull(card.getValidEndTime());
        assertNull(card.getActivateTime(), "发卡得到的是待开卡");
        assertEquals(STORE_ID, card.getStoreId(), "适用门店与开卡门店是两个字段");
        assertEquals(OPEN_STORE_ID, card.getOpenStoreId());
    }

    @Test
    @DisplayName("发卡：年卡不写适用门店与次数字段，有效期数量固定 1 年")
    void create_annualCard_shouldSkipTimesFields() {
        memberCardService.create(annualCardDTO());

        MemberCardDO card = latestCard();
        assertEquals(Integer.valueOf(2), card.getCardType());
        assertEquals(Integer.valueOf(2), card.getCourseScope());
        assertNull(card.getStoreId());
        assertNull(card.getPurchaseTimes());
        assertNull(card.getGiftTimes());
        assertNull(card.getRemainingTimes());
        assertEquals(Integer.valueOf(1), card.getValidPeriodCount(), "年卡有效期数量由服务端固定");
        assertCardNo(card.getCardNo());
    }

    @Test
    @DisplayName("发卡：次数卡缺适用门店／缺次数／合计为 0／有效期非 3、6、12 → 400；年卡提交次数类字段 → 400")
    void create_withInvalidCombination_shouldFail() {
        MemberCardCreateDTO withoutStore = timesCardDTO();
        withoutStore.setStoreId(null);
        assertMessage(withoutStore, 400, "次数卡必须选择适用门店");

        MemberCardCreateDTO withoutTimes = timesCardDTO();
        withoutTimes.setPurchaseTimes(null);
        assertMessage(withoutTimes, 400, "次数卡必须填写购买次数与赠送次数");

        MemberCardCreateDTO zeroTotal = timesCardDTO();
        zeroTotal.setPurchaseTimes(0);
        zeroTotal.setGiftTimes(0);
        assertMessage(zeroTotal, 400, "购买次数与赠送次数合计必须大于 0");

        MemberCardCreateDTO badPeriod = timesCardDTO();
        badPeriod.setValidPeriodCount(4);
        assertMessage(badPeriod, 400, "次数卡有效期数量只能是 3、6 或 12 个月");

        MemberCardCreateDTO annualWithStore = annualCardDTO();
        annualWithStore.setStoreId(STORE_ID);
        assertMessage(annualWithStore, 400, "年卡不能选择适用门店");

        MemberCardCreateDTO annualWithTimes = annualCardDTO();
        annualWithTimes.setPurchaseTimes(1);
        assertMessage(annualWithTimes, 400, "年卡不能填写购买次数与赠送次数");

        MemberCardCreateDTO annualWithPeriod = annualCardDTO();
        annualWithPeriod.setValidPeriodCount(12);
        assertMessage(annualWithPeriod, 400, "年卡不能填写有效期数量，有效期固定 1 年");

        MemberCardCreateDTO badCardType = timesCardDTO();
        badCardType.setCardType(3);
        assertMessage(badCardType, 400, "卡类型取值为 1 或 2");

        MemberCardCreateDTO badCourseScope = timesCardDTO();
        badCourseScope.setCourseScope(3);
        assertMessage(badCourseScope, 400, "适用课种范围取值为 1 或 2");

        assertTrue(allCards().isEmpty(), "校验失败不落库");
    }

    @Test
    @DisplayName("发卡：会员、开卡门店或适用门店不存在 → 404，且不落库")
    void create_withMissingRelations_shouldReportNotFound() {
        MemberCardCreateDTO missingMember = timesCardDTO();
        missingMember.setMemberId(UNKNOWN_ID);
        assertMessage(missingMember, 404, "会员不存在或已被删除");

        MemberCardCreateDTO missingOpenStore = timesCardDTO();
        missingOpenStore.setOpenStoreId(UNKNOWN_ID);
        assertMessage(missingOpenStore, 404, "开卡门店不存在或已被删除");

        MemberCardCreateDTO missingStore = timesCardDTO();
        missingStore.setStoreId(UNKNOWN_ID);
        assertMessage(missingStore, 404, "适用门店不存在或已被删除");

        assertTrue(allCards().isEmpty());
    }

    @Test
    @DisplayName("发卡：同一开卡门店当前时间片已有发卡 → 409 卡号冲突，不覆盖旧卡")
    void create_withCardNoConflict_shouldReportConflict() {
        LocalDateTime now = LocalDateTime.now();
        // 覆盖当前秒与后两秒：服务端在真正生成卡号时读时间，落在这个区间内即冲突
        for (int offset = 0; offset < 3; offset++) {
            insertCard(3000L + offset, cardNoGenerator.generate(OPEN_STORE_NO, now.plusSeconds(offset)));
        }

        ServiceException exception = assertThrows(ServiceException.class,
                () -> memberCardService.create(timesCardDTO()));
        assertEquals(Integer.valueOf(409), exception.getCode());
        assertEquals("该门店当前时间已有发卡记录，请稍后重试", exception.getMessage());
        assertEquals(3L, allCards().size(), "冲突时不覆盖已有卡");
    }

    @Test
    @DisplayName("开卡：次数卡按「月×30 天」写有效期单位与起止，有效期含首尾日")
    void activate_timesCard_shouldWriteValidityPeriod() {
        memberCardService.create(timesCardDTO());
        Long cardId = latestCard().getId();

        MemberCardDetailVO detail = memberCardService.activate(cardId);

        LocalDate today = LocalDate.now();
        assertEquals(Integer.valueOf(1), memberCardDao.selectById(cardId).getValidPeriodUnit(), "次数卡单位是月");
        assertEquals(today.atStartOfDay(), detail.getValidStartTime());
        assertEquals(today.plusDays(359).atTime(23, 59, 59), detail.getValidEndTime(), "12 个月 = 360 天，含首尾日");
        assertNotNull(detail.getActivateTime());
        assertEquals(Integer.valueOf(12), detail.getRemainingTimes(), "开卡不改剩余次数");
        assertNull(detail.getRemainingDays(), "剩余天数只给年卡");
        assertEquals("次数卡", detail.getCardTypeText());
        assertEquals("团课类", detail.getCourseScopeName());
    }

    @Test
    @DisplayName("开卡：年卡单位是年、有效期 365 天，剩余天数含今天")
    void activate_annualCard_shouldWriteYearPeriodAndRemainingDays() {
        memberCardService.create(annualCardDTO());
        Long cardId = latestCard().getId();

        MemberCardDetailVO detail = memberCardService.activate(cardId);

        LocalDate today = LocalDate.now();
        assertEquals(Integer.valueOf(2), memberCardDao.selectById(cardId).getValidPeriodUnit(), "年卡单位是年");
        assertEquals(today.atStartOfDay(), detail.getValidStartTime());
        assertEquals(today.plusDays(364).atTime(23, 59, 59), detail.getValidEndTime());
        assertEquals(Integer.valueOf(365), detail.getRemainingDays(), "剩余天数含首尾日");
        assertNull(detail.getStoreId());
        assertNull(detail.getStoreName());
        assertNull(detail.getPurchaseTimes());
    }

    @Test
    @DisplayName("开卡：重复开卡 → 409；已停卡不能开卡 → 409；卡不存在 → 404")
    void activate_shouldRejectIllegalState() {
        memberCardService.create(timesCardDTO());
        Long cardId = latestCard().getId();
        memberCardService.activate(cardId);

        ServiceException activated = assertThrows(ServiceException.class, () -> memberCardService.activate(cardId));
        assertEquals(Integer.valueOf(409), activated.getCode());
        assertEquals("该会员卡已开卡，不能重复开卡", activated.getMessage());

        memberCardService.create(timesCardDTO(STORE_ID, OPEN_STORE_ID));
        Long stoppedId = latestCard().getId();
        memberCardService.stop(stoppedId, stopDTO("客户申请停卡"));
        ServiceException stopped = assertThrows(ServiceException.class, () -> memberCardService.activate(stoppedId));
        assertEquals(Integer.valueOf(409), stopped.getCode());
        assertEquals("该会员卡已停卡，不能开卡", stopped.getMessage());

        assertMessage(UNKNOWN_ID, 404, "会员卡不存在或已被删除");
    }

    @Test
    @DisplayName("停卡：写入原因、保留剩余次数与有效期；重复停卡 → 409；原因缺失 → 400")
    void stop_shouldWriteReasonAndKeepTimes() {
        memberCardService.create(timesCardDTO());
        Long cardId = latestCard().getId();
        memberCardService.activate(cardId);
        MemberCardDetailVO activated = memberCardService.getById(cardId);

        MemberCardDetailVO stopped = memberCardService.stop(cardId, stopDTO("  客户申请停卡  "));

        assertNotNull(stopped.getStopTime());
        assertEquals("客户申请停卡", stopped.getStopReason(), "原因去掉前后空格");
        assertEquals(activated.getRemainingTimes(), stopped.getRemainingTimes());
        assertEquals(activated.getValidStartTime(), stopped.getValidStartTime());
        assertEquals(activated.getValidEndTime(), stopped.getValidEndTime());

        ServiceException repeated = assertThrows(ServiceException.class,
                () -> memberCardService.stop(cardId, stopDTO("再次停卡")));
        assertEquals(Integer.valueOf(409), repeated.getCode());
        assertEquals("该会员卡已停卡，不能重复停卡", repeated.getMessage());

        MemberCardStopDTO blank = new MemberCardStopDTO();
        blank.setStopReason("   ");
        memberCardService.create(timesCardDTO(STORE_ID, OPEN_STORE_ID));
        Long pendingId = latestCard().getId();
        assertEquals(Integer.valueOf(400),
                assertThrows(ServiceException.class, () -> memberCardService.stop(pendingId, blank)).getCode());

        assertMessage(UNKNOWN_ID, 404, "会员卡不存在或已被删除");
    }

    @Test
    @DisplayName("列表与详情：枚举成对返回、次数卡与年卡字段分流；会员或门店已删除时按空字符串兜底")
    void pageAndDetail_shouldTranslateAndTolerateDeletedRelations() {
        memberCardService.create(timesCardDTO());
        Long timesCardId = latestCard().getId();
        memberCardService.create(annualCardDTO(STORE_ID));
        Long annualCardId = latestCard().getId();

        PageResult<MemberCardListItemVO> page = memberCardService.page(query());
        assertEquals(Long.valueOf(2), page.getTotal());
        assertEquals(annualCardId, page.getList().get(0).getId(), "按 ID 降序：最新发卡在前");

        MemberCardListItemVO annual = page.getList().get(0);
        assertEquals("年卡", annual.getCardTypeText());
        assertEquals("私教课", annual.getCourseScopeName());
        assertNull(annual.getRemainingTimes());
        assertNull(annual.getTotalTimes());
        assertNull(annual.getStoreId());

        MemberCardListItemVO times = page.getList().get(1);
        assertEquals("次数卡", times.getCardTypeText());
        assertEquals("0001", times.getOpenStoreNo());
        assertEquals("徐汇店", times.getOpenStoreName());
        assertEquals("浦东店", times.getStoreName());
        assertEquals("忻会员8000", times.getMemberName());
        assertEquals(Integer.valueOf(12), times.getTotalTimes());
        assertNull(times.getValidStartTime(), "未开卡没有有效期");

        MemberCardQuery byType = query();
        byType.setCardType(1);
        assertEquals(1, memberCardService.page(byType).getList().size());
        MemberCardQuery byStore = query();
        byStore.setStoreId(STORE_ID);
        assertEquals(1, memberCardService.page(byStore).getList().size());
        MemberCardQuery byMember = query();
        byMember.setMemberId(UNKNOWN_ID);
        assertEquals(0, memberCardService.page(byMember).getList().size());

        // 会员与适用门店被删除：卡记录仍返回，关联名称为空字符串，ID 保留
        jdbcTemplate.update("DELETE FROM t_member WHERE id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM t_store WHERE id = ?", STORE_ID);
        MemberCardDetailVO afterDelete = memberCardService.getById(timesCardId);
        assertEquals("", afterDelete.getMemberName());
        assertEquals(MEMBER_ID, afterDelete.getMemberId());
        assertEquals("0001", afterDelete.getOpenStoreNo(), "开卡门店仍在，编号与名称照常返回");
        assertEquals(STORE_ID, afterDelete.getStoreId(), "适用门店 ID 保留");
        assertEquals("", afterDelete.getStoreName());

        // 开卡门店也删除：编号与名称为空字符串，ID 仍保留
        jdbcTemplate.update("DELETE FROM t_store WHERE id = ?", OPEN_STORE_ID);
        MemberCardDetailVO withoutOpenStore = memberCardService.getById(timesCardId);
        assertEquals(OPEN_STORE_ID, withoutOpenStore.getOpenStoreId());
        assertEquals("", withoutOpenStore.getOpenStoreNo());
        assertEquals("", withoutOpenStore.getOpenStoreName());

        assertMessage(UNKNOWN_ID, 404, "会员卡不存在或已被删除");
    }

    @Test
    @DisplayName("删除：物理删除卡行，再删 → 404")
    void delete_shouldRemoveRow() {
        memberCardService.create(timesCardDTO());
        Long cardId = latestCard().getId();

        memberCardService.delete(cardId);

        assertNull(memberCardDao.selectById(cardId));
        assertMessage(cardId, 404, "会员卡不存在或已被删除");
        assertMessage(UNKNOWN_ID, 404, "会员卡不存在或已被删除");
    }

    private void assertCardNo(String cardNo) {
        assertNotNull(cardNo);
        assertEquals(18, cardNo.length(), "卡号共 18 位：" + cardNo);
        assertTrue(cardNo.matches("\\d{18}"), "卡号必须是 18 位数字：" + cardNo);
        String today = LocalDate.now().toString().replace("-", "");
        assertEquals(today, cardNo.substring(0, 8), "卡号前 8 位是发卡日期");
        assertEquals(OPEN_STORE_NO, cardNo.substring(8, 12), "卡号第 9~12 位是开卡门店编号");
        assertTrue(Integer.parseInt(cardNo.substring(12, 14)) <= 23, "卡号时间是 24 小时制：" + cardNo.substring(12));
    }

    private MemberCardCreateDTO timesCardDTO() {
        return timesCardDTO(OPEN_STORE_ID, STORE_ID);
    }

    /**
     * 指定开卡门店与适用门店的发卡请求：卡号按开卡门店编号与发卡时间组成，
     * 同一秒内同一开卡门店铺第二张会卡号冲突，因此需要连续发卡时换开卡门店。
     */
    private MemberCardCreateDTO timesCardDTO(Long openStoreId, Long storeId) {
        MemberCardCreateDTO dto = new MemberCardCreateDTO();
        dto.setMemberId(MEMBER_ID);
        dto.setOpenStoreId(openStoreId);
        dto.setCardName(" 普拉提次卡 ");
        dto.setCardType(1);
        dto.setCourseScope(1);
        dto.setStoreId(storeId);
        dto.setPurchaseTimes(10);
        dto.setGiftTimes(2);
        dto.setValidPeriodCount(12);
        return dto;
    }

    private MemberCardCreateDTO annualCardDTO() {
        return annualCardDTO(OPEN_STORE_ID);
    }

    private MemberCardCreateDTO annualCardDTO(Long openStoreId) {
        MemberCardCreateDTO dto = new MemberCardCreateDTO();
        dto.setMemberId(MEMBER_ID);
        dto.setOpenStoreId(openStoreId);
        dto.setCardName("私教年卡");
        dto.setCardType(2);
        dto.setCourseScope(2);
        return dto;
    }

    private MemberCardStopDTO stopDTO(String reason) {
        MemberCardStopDTO dto = new MemberCardStopDTO();
        dto.setStopReason(reason);
        return dto;
    }

    private MemberCardQuery query() {
        MemberCardQuery query = new MemberCardQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        return query;
    }

    private List<MemberCardDO> allCards() {
        IPage<MemberCardDO> page = memberCardDao.selectPage(query());
        return page.getRecords();
    }

    /** 最新发卡的那张卡（雪花 ID 递增，取列表首行） */
    private MemberCardDO latestCard() {
        return allCards().get(0);
    }

    private void assertMessage(MemberCardCreateDTO dto, int code, String message) {
        ServiceException exception = assertThrows(ServiceException.class, () -> memberCardService.create(dto));
        assertEquals(Integer.valueOf(code), exception.getCode());
        assertEquals(message, exception.getMessage());
    }

    private void assertMessage(Long cardId, int code, String message) {
        ServiceException exception = assertThrows(ServiceException.class, () -> memberCardService.getById(cardId));
        assertEquals(Integer.valueOf(code), exception.getCode());
        assertEquals(message, exception.getMessage());
    }

    private void insertMember() {
        jdbcTemplate.update("INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by,"
                + " create_time, update_by, update_time) VALUES (?, '13800138000', NULL, '8000', '忻会员8000', 0, 0,"
                + " 1, NOW(), 1, NOW())", MEMBER_ID);
    }

    private void insertStore(Long id, String storeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                        + " address, image_url, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, 1, ?,"
                        + " '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号', NULL, 1, NOW(), 1, NOW())",
                id, storeNo, name, REGION_CODE);
    }

    private void insertCard(Long id, String cardNo) {
        jdbcTemplate.update("INSERT INTO t_member_card (id, member_id, open_store_id, card_no, card_name, card_type,"
                + " course_scope, store_id, purchase_times, gift_times, remaining_times, valid_period_count,"
                + " create_by, create_time, update_by, update_time) VALUES (?, ?, ?, ?, '已发卡', 1, 1, ?, 10, 2, 12,"
                + " 12, 1, NOW(), 1, NOW())", id, MEMBER_ID, OPEN_STORE_ID, cardNo, STORE_ID);
    }
}
