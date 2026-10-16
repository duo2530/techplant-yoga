package com.techplant.yoga.membercard.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ContextConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.query.MemberCardQuery;

/** 会员卡数据访问的集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberCardDaoImpl.class})
@DisplayName("MemberCardDao 集成测试（Testcontainers MySQL：列映射 / 审计 / 卡号唯一 / 状态条件更新）")
class MemberCardDaoTest extends AbstractYogaIntegrationTest {
    private static final Long MEMBER_ID = 1856739201475235902L;

    private static final Long OPEN_STORE_ID = 1856739201475235901L;

    private static final Long STORE_ID = 1856739201475235903L;

    @Autowired
    private MemberCardDao memberCardDao;

    @BeforeEach
    void resetTable() {
        YogaSchema.reset(dataSource, "t_member_card");
    }

    @Test
    @DisplayName("新增：雪花 ID ＋ 审计填充；列名按下划线→驼峰映射")
    void insert_shouldGenerateSnowflakeIdAndFillAuditFields() {
        MemberCardDO card = newCard(1001L, "202610030001100000");

        int rows = memberCardDao.insert(card);

        assertEquals(1, rows);
        assertNotNull(card.getId());
        assertEquals(OPERATOR_ID, card.getCreateBy(), "创建人应由 MetaObjectHandler 填充");
        assertNotNull(card.getCreateTime(), "创建时间应由 MetaObjectHandler 填充");

        MemberCardDO found = memberCardDao.selectById(1001L);
        assertNotNull(found);
        assertEquals("202610030001100000", found.getCardNo());
        assertEquals(MEMBER_ID, found.getMemberId());
        assertEquals(OPEN_STORE_ID, found.getOpenStoreId());
        assertEquals(STORE_ID, found.getStoreId());
        assertEquals(Integer.valueOf(1), found.getCardType());
        assertEquals(Integer.valueOf(1), found.getCourseScope());
        assertEquals(Integer.valueOf(10), found.getPurchaseTimes());
        assertEquals(Integer.valueOf(2), found.getGiftTimes());
        assertEquals(Integer.valueOf(12), found.getRemainingTimes());
        assertEquals(Integer.valueOf(12), found.getValidPeriodCount());
        assertNull(found.getValidPeriodUnit(), "发卡时不写有效期单位");
        assertNull(found.getValidStartTime());
        assertNull(found.getValidEndTime());
        assertNull(found.getActivateTime());
        assertNull(found.getStopTime());
        assertNull(found.getStopReason());
    }

    @Test
    @DisplayName("新增：卡号重复 → DuplicateKeyException（上层转 409 的兜底）")
    void insert_withDuplicateCardNo_shouldThrowDuplicateKey() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));

        assertThrows(DuplicateKeyException.class, () -> memberCardDao.insert(newCard(1002L, "202610030001100000")));
    }

    @Test
    @DisplayName("列表：按会员、卡号模糊、卡类型与适用门店筛选；按 ID 降序分页")
    void selectPage_shouldFilterAndOrderByIdDesc() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));
        MemberCardDO annual = newCard(1002L, "202610040001110000");
        annual.setMemberId(9999L);
        annual.setCardType(2);
        annual.setCourseScope(2);
        annual.setStoreId(null);
        annual.setPurchaseTimes(null);
        annual.setGiftTimes(null);
        annual.setRemainingTimes(null);
        annual.setValidPeriodCount(1);
        memberCardDao.insert(annual);

        IPage<MemberCardDO> firstPage = memberCardDao.selectPage(query(1, 1));
        assertEquals(2L, firstPage.getTotal());
        assertEquals(Arrays.asList(1002L), ids(firstPage.getRecords()), "按 ID 降序：最新发卡在前");
        assertEquals(2, firstPage.getRecords().get(0).getCardType());
        assertNull(firstPage.getRecords().get(0).getStoreId(), "年卡没有适用门店");

        MemberCardQuery byMember = query(1, 10);
        byMember.setMemberId(MEMBER_ID);
        assertEquals(Arrays.asList(1001L), ids(memberCardDao.selectPage(byMember).getRecords()));

        MemberCardQuery byCardNo = query(1, 10);
        byCardNo.setCardNo("100000");
        assertEquals(Arrays.asList(1001L), ids(memberCardDao.selectPage(byCardNo).getRecords()));

        MemberCardQuery byType = query(1, 10);
        byType.setCardType(2);
        assertEquals(Arrays.asList(1002L), ids(memberCardDao.selectPage(byType).getRecords()));

        MemberCardQuery byStore = query(1, 10);
        byStore.setStoreId(STORE_ID);
        assertEquals(Arrays.asList(1001L), ids(memberCardDao.selectPage(byStore).getRecords()));

        MemberCardQuery blankCardNo = query(1, 10);
        blankCardNo.setCardNo("   ");
        assertEquals(2, memberCardDao.selectPage(blankCardNo).getRecords().size(), "空白卡号不参与筛选");
    }

    @Test
    @DisplayName("开卡：写入开卡时间、有效期单位与起止；已开卡或已停卡的卡影响行数为 0")
    void activate_shouldOnlyApplyToUnactivatedCard() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));
        LocalDateTime start = LocalDateTime.of(2026, 10, 3, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2027, 9, 27, 23, 59, 59);
        LocalDateTime activateTime = LocalDateTime.of(2026, 10, 3, 10, 30, 0);

        assertEquals(1, memberCardDao.activate(1001L, 1, start, end, activateTime, OPERATOR_ID));

        MemberCardDO activated = memberCardDao.selectById(1001L);
        assertEquals(Integer.valueOf(1), activated.getValidPeriodUnit());
        assertEquals(start, activated.getValidStartTime());
        assertEquals(end, activated.getValidEndTime());
        assertEquals(activateTime, activated.getActivateTime());
        assertEquals(OPERATOR_ID, activated.getUpdateBy());
        assertEquals(Integer.valueOf(12), activated.getRemainingTimes(), "开卡不改剩余次数");

        assertEquals(0, memberCardDao.activate(1001L, 1, start, end, activateTime, OPERATOR_ID), "重复开卡不生效");

        memberCardDao.insert(newCard(1003L, "202610030001120000"));
        memberCardDao.stop(1003L, activateTime, "客户申请停卡", OPERATOR_ID);
        assertEquals(0, memberCardDao.activate(1003L, 1, start, end, activateTime, OPERATOR_ID), "已停卡不能再开卡");
    }

    @Test
    @DisplayName("停卡：写入停卡时间与原因并保留剩余次数与有效期；重复停卡影响行数为 0")
    void stop_shouldKeepTimesAndValidity() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));
        LocalDateTime stopTime = LocalDateTime.of(2026, 10, 4, 10, 0, 0);

        assertEquals(1, memberCardDao.stop(1001L, stopTime, "客户申请停卡", OPERATOR_ID));

        MemberCardDO stopped = memberCardDao.selectById(1001L);
        assertEquals(stopTime, stopped.getStopTime());
        assertEquals("客户申请停卡", stopped.getStopReason());
        assertEquals(Integer.valueOf(12), stopped.getRemainingTimes());
        assertEquals(Integer.valueOf(12), stopped.getValidPeriodCount());
        assertEquals(OPERATOR_ID, stopped.getUpdateBy());

        assertEquals(0, memberCardDao.stop(1001L, stopTime, "再次停卡", OPERATOR_ID));
        assertEquals("客户申请停卡", memberCardDao.selectById(1001L).getStopReason());
    }

    @Test
    @DisplayName("当前读：存在的卡返回该行，不存在返回 null；查询缺失主键不抛异常")
    void selectByIdForUpdate_shouldReturnRowOrNull() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));

        assertNotNull(memberCardDao.selectByIdForUpdate(1001L));
        assertNull(memberCardDao.selectByIdForUpdate(999L));
        assertNull(memberCardDao.selectByIdForUpdate(null));
        assertNull(memberCardDao.selectById(null));
    }

    @Test
    @DisplayName("删除：物理删除卡行，再删影响行数为 0")
    void deleteById_shouldRemoveRow() {
        memberCardDao.insert(newCard(1001L, "202610030001100000"));

        assertEquals(1, memberCardDao.deleteById(1001L));
        assertNull(memberCardDao.selectById(1001L));
        assertEquals(0, memberCardDao.deleteById(1001L));
        assertTrue(memberCardDao.selectPage(query(1, 10)).getRecords().isEmpty());
    }

    private MemberCardQuery query(int pageNum, int pageSize) {
        MemberCardQuery query = new MemberCardQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return query;
    }

    private List<Long> ids(List<MemberCardDO> cards) {
        List<Long> ids = new ArrayList<Long>();
        for (MemberCardDO card : cards) {
            ids.add(card.getId());
        }
        return ids;
    }

    private MemberCardDO newCard(Long id, String cardNo) {
        MemberCardDO card = new MemberCardDO();
        card.setId(id);
        card.setMemberId(MEMBER_ID);
        card.setOpenStoreId(OPEN_STORE_ID);
        card.setCardNo(cardNo);
        card.setCardName("普拉提次卡");
        card.setCardType(1);
        card.setCourseScope(1);
        card.setStoreId(STORE_ID);
        card.setPurchaseTimes(10);
        card.setGiftTimes(2);
        card.setRemainingTimes(12);
        card.setValidPeriodCount(12);
        return card;
    }
}
