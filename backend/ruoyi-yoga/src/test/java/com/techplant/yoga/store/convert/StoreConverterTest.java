package com.techplant.yoga.store.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;

/** 门店对象转换单元测试，不连接数据库。 */
@DisplayName("StoreConverter 单元测试")
class StoreConverterTest
{
    @Test
    @DisplayName("新增请求映射为默认启用且未删除的门店")
    void toDO_shouldMapFieldsAndSetDefaults()
    {
        StoreCreateDTO dto = createDTO();

        StoreDO store = StoreConverter.toDO(dto);

        assertEquals("徐汇店", store.getName());
        assertEquals("上海市徐汇区", store.getRegion());
        assertEquals("310000", store.getProvinceCode());
        assertEquals("310100", store.getCityCode());
        assertEquals("310104", store.getDistrictCode());
        assertEquals("漕溪北路 88 号", store.getAddress());
        assertEquals("021-12345678", store.getPhone());
        assertEquals(Integer.valueOf(1), store.getBusinessType());
        assertEquals(Integer.valueOf(1), store.getStoreType());
        assertEquals("周一至周日 09:00-22:00", store.getBusinessHours());
        assertEquals(Integer.valueOf(1), store.getStatus());
        assertEquals(Integer.valueOf(0), store.getDeleted());
        assertNull(store.getId());
    }

    @Test
    @DisplayName("修改请求覆盖七个业务字段")
    void applyUpdate_shouldOverwriteFields()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        StoreUpdateDTO dto = new StoreUpdateDTO();
        dto.setName("静安精品店");
        dto.setRegion("上海市静安区");
        dto.setProvinceCode("310000");
        dto.setCityCode("310100");
        dto.setDistrictCode("310106");
        dto.setAddress("愚园路 168 号");
        dto.setPhone("021-87654321");
        dto.setBusinessType(2);
        dto.setStoreType(2);
        dto.setBusinessHours("周一至周日 10:00-21:00");

        StoreConverter.applyUpdate(store, dto);

        assertEquals("静安精品店", store.getName());
        assertEquals("上海市静安区", store.getRegion());
        assertEquals("310106", store.getDistrictCode());
        assertEquals("愚园路 168 号", store.getAddress());
        assertEquals(Integer.valueOf(2), store.getBusinessType());
        assertEquals(Integer.valueOf(2), store.getStoreType());
    }

    @Test
    @DisplayName("VO 不暴露审计字段，日志摘要不包含电话和地址原文")
    void voAndSnapshot_shouldNotExposeAuditOrSensitiveText()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        store.setId(1001L);

        StoreListItemVO listVO = StoreConverter.toListItemVO(store);
        StoreDetailVO detailVO = StoreConverter.toDetailVO(store);
        String snapshot = StoreConverter.snapshot(store);

        assertEquals(1001L, listVO.getId());
        assertEquals("漕溪北路 88 号", detailVO.getAddress());
        assertFalse(hasField(StoreListItemVO.class, "createBy"));
        assertFalse(hasField(StoreDetailVO.class, "updateBy"));
        assertTrue(snapshot.contains("phone=有"), snapshot);
        assertTrue(snapshot.contains("address=有"), snapshot);
        assertFalse(snapshot.contains("021-12345678"), snapshot);
        assertFalse(snapshot.contains("漕溪北路"), snapshot);
    }

    @Test
    @DisplayName("游客门店 VO 只包含展示字段，不包含后台状态")
    void toPublicItemVO_shouldExcludeAdminFields()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        store.setId(1001L);
        store.setStatus(1);

        StorePublicItemVO vo = StoreConverter.toPublicItemVO(store);

        assertEquals("310104", vo.getDistrictCode());
        assertEquals("徐汇店", vo.getName());
        assertFalse(hasField(StorePublicItemVO.class, "status"));
        assertFalse(hasField(StorePublicItemVO.class, "deleted"));
    }

    private StoreCreateDTO createDTO()
    {
        StoreCreateDTO dto = new StoreCreateDTO();
        dto.setName(" 徐汇店 ");
        dto.setRegion("上海市徐汇区");
        dto.setProvinceCode("310000");
        dto.setCityCode("310100");
        dto.setDistrictCode("310104");
        dto.setAddress("漕溪北路 88 号");
        dto.setPhone("021-12345678");
        dto.setBusinessType(1);
        dto.setStoreType(1);
        dto.setBusinessHours("周一至周日 09:00-22:00");
        return dto;
    }

    private boolean hasField(Class<?> type, String fieldName)
    {
        try
        {
            type.getDeclaredField(fieldName);
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }
}
