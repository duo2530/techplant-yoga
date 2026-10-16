package com.techplant.yoga.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.testcontainers.AbstractYogaIntegrationTest;
import com.techplant.yoga.common.testcontainers.YogaMyBatisTestConfig;
import com.techplant.yoga.common.testcontainers.YogaSchema;
import com.techplant.yoga.store.dao.StoreDao;
import com.techplant.yoga.store.dao.StoreDaoImpl;
import com.techplant.yoga.store.dao.StoreRegionDictDaoImpl;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.service.impl.StoreServiceImpl;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 门店编号分配的集成测试（Testcontainers MySQL，表结构取自 backend/sql/yoga.sql，不连开发库）。
 *
 * <p>计数器是编号的唯一来源，用例自行准备计数器与区域字典夹具；
 * 教室与排课服务在 {@code StoreServiceImpl} 上是延迟注入，删除门禁不在本用例覆盖范围。</p>
 */
@ContextConfiguration(classes = {YogaMyBatisTestConfig.class, StoreDaoImpl.class, StoreRegionDictDaoImpl.class,
    StoreServiceImpl.class})
@DisplayName("StoreService 集成测试（Testcontainers MySQL：编号分配 / 上限 / 冲突归类 / 编号不可修改）")
class StoreServiceImplTest extends AbstractYogaIntegrationTest {
    private static final String REGION_CODE = "310104";

    @Autowired
    private StoreService storeService;

    @Autowired
    private StoreDao storeDao;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        YogaSchema.reset(dataSource, "t_store", "t_store_no_counter", "sys_dict_data");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class,"
                + " list_class, is_default, status, create_by, create_time) VALUES (1, '徐汇区', ?, 'store_region',"
                + " '', 'default', 'N', '0', 'admin', NOW())", REGION_CODE);
    }

    @Test
    @DisplayName("新增：编号从计数器递增，补零为四位；连续新增不重号")
    void create_shouldAllocateSequentialStoreNo() {
        insertCounter(3);

        storeService.create(createDTO("徐汇新店"));
        storeService.create(createDTO("浦东新店"));

        assertEquals("0004", storeDao.selectByName("徐汇新店").getStoreNo());
        assertEquals("0005", storeDao.selectByName("浦东新店").getStoreNo());
        assertEquals(Integer.valueOf(5), counterValue(), "计数器应随新增递增");
        assertEquals(Integer.valueOf(1),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_store WHERE store_no = '0004'", Integer.class));
    }

    @Test
    @DisplayName("新增：计数器已到 9999 → 409 编号已用尽，且不落库")
    void create_whenCounterExhausted_shouldReportConflict() {
        insertCounter(9999);

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.create(createDTO("徐汇新店")));
        assertEquals(Integer.valueOf(409), exception.getCode());
        assertEquals("门店编号已用尽", exception.getMessage());
        assertNull(storeDao.selectByName("徐汇新店"));
        assertEquals(Integer.valueOf(9999), counterValue(), "编号用尽时计数器不应再变化");
    }

    @Test
    @DisplayName("新增：计数器未初始化 → 500，不退回按门店表最大值推算")
    void create_whenCounterMissing_shouldFail() {
        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.create(createDTO("徐汇新店")));
        assertEquals(Integer.valueOf(500), exception.getCode());
        assertEquals("门店编号计数器未初始化，无法新增门店", exception.getMessage());
        assertNull(storeDao.selectByName("徐汇新店"));
    }

    @Test
    @DisplayName("新增：分配到的编号已被占用（uk_store_no 兜底）→ 409 编号冲突，不与名称重复混淆")
    void create_whenStoreNoTaken_shouldReportNoConflict() {
        insertCounter(3);
        insertStore(2001L, "0004", "已占号门店");

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.create(createDTO("徐汇新店")));
        assertEquals(Integer.valueOf(409), exception.getCode());
        assertEquals("门店编号生成冲突，请稍后重试", exception.getMessage());
        assertNull(storeDao.selectByName("徐汇新店"));
    }

    @Test
    @DisplayName("新增：名称重复 → 409 名称已存在，编号不被消耗")
    void create_withDuplicateName_shouldReportConflict() {
        insertCounter(3);
        insertStore(2001L, "0001", "徐汇店");

        ServiceException exception = assertThrows(ServiceException.class, () -> storeService.create(createDTO("徐汇店")));
        assertEquals(Integer.valueOf(409), exception.getCode());
        assertEquals("门店名称已存在", exception.getMessage());
        assertEquals(Integer.valueOf(3), counterValue(), "名称重复时不应消耗门店编号");
    }

    @Test
    @DisplayName("修改：业务字段被覆盖，门店编号保持不变")
    void update_shouldKeepStoreNo() {
        insertCounter(3);
        storeService.create(createDTO("徐汇新店"));
        StoreDO created = storeDao.selectByName("徐汇新店");

        StoreUpdateDTO dto = updateDTO("徐汇改名店");
        StoreDetailVO detail = storeService.update(created.getId(), dto);

        assertEquals("0004", detail.getStoreNo());
        assertEquals("徐汇改名店", detail.getName());
        assertEquals("0004", storeDao.selectById(created.getId()).getStoreNo(), "编号不随修改变化");
    }

    @Test
    @DisplayName("跨模块摘要：返回 id、门店编号与名称；已删除门店不在结果中")
    void summaries_shouldReturnStoreNo() {
        insertCounter(3);
        storeService.create(createDTO("徐汇新店"));
        StoreDO created = storeDao.selectByName("徐汇新店");

        List<StoreSummaryVO> summaries = storeService.summaries(Arrays.asList(created.getId(), 999999L));
        assertEquals(1, summaries.size());
        assertEquals(created.getId(), summaries.get(0).getId());
        assertEquals("0004", summaries.get(0).getStoreNo());
        assertEquals("徐汇新店", summaries.get(0).getName());

        assertNotNull(storeService.summaries(null));
        assertEquals(0, storeService.summaries(null).size());
    }

    private StoreCreateDTO createDTO(String name) {
        StoreCreateDTO dto = new StoreCreateDTO();
        dto.setName(name);
        dto.setStoreType(1);
        dto.setRegionCode(REGION_CODE);
        dto.setPhone("021-12345678");
        dto.setBusinessHours("周一至周日 09:00-22:00");
        dto.setAddress("漕溪北路 88 号");
        return dto;
    }

    private StoreUpdateDTO updateDTO(String name) {
        StoreUpdateDTO dto = new StoreUpdateDTO();
        dto.setName(name);
        dto.setStoreType(2);
        dto.setRegionCode(REGION_CODE);
        dto.setPhone("021-87654321");
        dto.setBusinessHours("周一至周日 10:00-21:00");
        dto.setAddress("愚园路 168 号");
        return dto;
    }

    private void insertCounter(int lastNo) {
        jdbcTemplate.update("INSERT INTO t_store_no_counter (id, last_no) VALUES (1, ?)", lastNo);
    }

    private Integer counterValue() {
        return jdbcTemplate.queryForObject("SELECT last_no FROM t_store_no_counter WHERE id = 1", Integer.class);
    }

    /** 直接落一条已占号的门店，制造 uk_store_no 冲突 */
    private void insertStore(Long id, String storeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO t_store (id, store_no, name, store_type, region_code, phone, business_hours,"
                        + " address, image_url, create_by, create_time, update_by, update_time) VALUES (?, ?, ?, 1, ?,"
                        + " '021-12345678', '周一至周日 09:00-22:00', '漕溪北路 88 号', NULL, 1, NOW(), 1, NOW())",
                id, storeNo, name, REGION_CODE);
    }
}
