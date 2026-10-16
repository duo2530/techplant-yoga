package com.techplant.yoga.member.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;

/** 会员等级字典数据访问的集成测试（Testcontainers MySQL，表结构取自 backend/sql/ry_20260417.sql，不连开发库）。 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, MemberLevelDictDaoImpl.class})
@DisplayName("MemberLevelDictDao 集成测试（Testcontainers MySQL：等级码 → 等级名称）")
class MemberLevelDictDaoTest extends AbstractYogaIntegrationTest {
    @Autowired
    private MemberLevelDictDao memberLevelDictDao;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTable() {
        YogaSchema.reset(dataSource, "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("映射：只取 member_level 且状态正常的记录，按字典排序返回，停用与非数字键值不参与")
    void selectLevelLabelMap_shouldReadEnabledLevelsOnly() {
        insertLevel(2, "V1 忻会员", "1", "0");
        insertLevel(1, "V0 普通会员", "0", "0");
        insertLevel(3, "V2 银会员", "2", "0");
        insertLevel(4, "V9 已停用等级", "9", "1");
        insertLevel(5, "等级值非法", "VIP", "0");
        insertOtherDictType();

        Map<Integer, String> labels = memberLevelDictDao.selectLevelLabelMap();

        assertEquals(Arrays.asList(0, 1, 2), new ArrayList<Integer>(labels.keySet()), "按 dict_sort 升序");
        assertEquals("V0 普通会员", labels.get(0));
        assertEquals("V1 忻会员", labels.get(1));
        assertEquals("V2 银会员", labels.get(2));
        assertFalse(labels.containsKey(9), "停用的等级不参与映射");
        assertEquals(3, labels.size());
    }

    @Test
    @DisplayName("字典没有等级行：返回空映射，不抛异常")
    void selectLevelLabelMap_withoutLevelRows_shouldReturnEmptyMap() {
        insertOtherDictType();

        assertTrue(memberLevelDictDao.selectLevelLabelMap().isEmpty());
    }

    private void insertLevel(int dictSort, String label, String value, String status) {
        jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                + " list_class, is_default, status, create_by, create_time) VALUES (?, ?, ?, 'member_level', '',"
                + " 'default', 'N', ?, 'admin', NOW())", dictSort, label, value, status);
    }

    private void insertOtherDictType() {
        jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                + " list_class, is_default, status, create_by, create_time) VALUES (1, '主力店', '1', 'store_type',"
                + " '', 'default', 'N', '0', 'admin', NOW())");
    }
}
