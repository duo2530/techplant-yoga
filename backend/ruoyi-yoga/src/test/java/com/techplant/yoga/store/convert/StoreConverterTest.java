package com.techplant.yoga.store.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 门店对象转换单元测试，不连接数据库、不连接 Redis（门店管理详细设计 §2.3）。
 *
 * <p>重点验证：字段映射与去空格、枚举<b>成对返回</b>（storeType + storeTypeName、
 * regionCode + regionName）、VO 不暴露审计字段、用户端 VO 不暴露状态字段且空 imageUrl 给空字符串。</p>
 */
@DisplayName("StoreConverter 单元测试")
class StoreConverterTest
{
    @Test
    @DisplayName("新增请求映射为门店 DO，并去掉前后空格")
    void toDO_shouldMapFieldsAndTrim()
    {
        StoreDO store = StoreConverter.toDO(createDTO());

        assertEquals("徐汇店", store.getName());
        assertEquals(Integer.valueOf(1), store.getStoreType());
        assertEquals("310104", store.getRegionCode());
        assertEquals("021-12345678", store.getPhone());
        assertEquals("周一至周日 09:00-22:00", store.getBusinessHours());
        assertEquals("漕溪北路 88 号", store.getAddress());
        assertEquals("https://cdn.example.com/store/1.jpg", store.getImageUrl());
        // 主键与审计字段由框架填充，转换层不赋值
        assertNull(store.getId());
        assertNull(store.getCreateBy());
        assertNull(store.getUpdateTime());
    }

    @Test
    @DisplayName("修改请求全量覆盖业务字段，且允许清空 imageUrl")
    void applyUpdate_shouldOverwriteFields()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        StoreUpdateDTO dto = new StoreUpdateDTO();
        dto.setName(" 静安精品店 ");
        dto.setStoreType(2);
        dto.setRegionCode("310106");
        dto.setPhone("021-87654321");
        dto.setBusinessHours("周一至周日 10:00-21:00");
        dto.setAddress("愚园路 168 号");
        dto.setImageUrl(null);

        StoreConverter.applyUpdate(store, dto);

        assertEquals("静安精品店", store.getName());
        assertEquals(Integer.valueOf(2), store.getStoreType());
        assertEquals("310106", store.getRegionCode());
        assertEquals("愚园路 168 号", store.getAddress());
        assertNull(store.getImageUrl());
    }

    @Test
    @DisplayName("管理端 VO 成对返回门店类型与所在区域，且不暴露审计字段")
    void toListItemVO_shouldPairEnumFields()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        store.setId(1001L);

        StoreListItemVO listVO = StoreConverter.toListItemVO(store, "徐汇区");
        StoreDetailVO detailVO = StoreConverter.toDetailVO(store, "徐汇区");

        assertEquals(1001L, listVO.getId());
        assertEquals(Integer.valueOf(1), listVO.getStoreType());
        assertEquals("主力店", listVO.getStoreTypeName());
        assertEquals("310104", listVO.getRegionCode());
        assertEquals("徐汇区", listVO.getRegionName());
        assertEquals(Integer.valueOf(1), detailVO.getStoreType());
        assertEquals("主力店", detailVO.getStoreTypeName());
        assertEquals("徐汇区", detailVO.getRegionName());
        assertFalse(hasField(StoreListItemVO.class, "createBy"));
        assertFalse(hasField(StoreDetailVO.class, "updateBy"));
        assertFalse(hasField(StoreDetailVO.class, "deleted"));
    }

    @Test
    @DisplayName("区名缺失时返回空字符串，不返回 null")
    void regionName_shouldFallbackToEmptyString()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        store.setRegionCode("310199");

        StoreListItemVO vo = StoreConverter.toListItemVO(store, null);

        assertEquals("", vo.getRegionName());
        assertEquals("", StoreConverter.regionNameOf(null, "310104"));
        assertEquals("", StoreConverter.regionNameOf(Collections.<String, String>emptyMap(), null));
    }

    @Test
    @DisplayName("批量转换按 regionCode 从字典映射翻译区名")
    void toListItemVOList_shouldTranslateRegionName()
    {
        StoreDO store = StoreConverter.toDO(createDTO());

        List<StoreListItemVO> list = StoreConverter.toListItemVOList(Collections.singletonList(store),
                Collections.singletonMap("310104", "徐汇区"));

        assertEquals(1, list.size());
        assertEquals("徐汇区", list.get(0).getRegionName());
    }

    @Test
    @DisplayName("用户端门店 VO 不含状态与审计字段，imageUrl 为空时给空字符串")
    void toPublicItemVO_shouldExcludeAdminFields()
    {
        StoreDO store = StoreConverter.toDO(createDTO());
        store.setId(1001L);
        store.setImageUrl(null);

        StorePublicItemVO vo = StoreConverter.toPublicItemVO(store, "徐汇区");

        assertEquals("徐汇店", vo.getName());
        assertEquals(Integer.valueOf(1), vo.getStoreType());
        assertEquals("主力店", vo.getStoreTypeName());
        assertEquals("310104", vo.getRegionCode());
        assertEquals("徐汇区", vo.getRegionName());
        assertEquals("", vo.getImageUrl());
        assertFalse(hasField(StorePublicItemVO.class, "status"));
        assertFalse(hasField(StorePublicItemVO.class, "deleted"));
        assertFalse(hasField(StorePublicItemVO.class, "createTime"));
        assertFalse(hasField(StorePublicItemVO.class, "updateTime"));
    }

    @Test
    @DisplayName("门店摘要只给 id 与 name")
    void toSummaryVOList_shouldOnlyCarryIdAndName()
    {
        StoreDO first = StoreConverter.toDO(createDTO());
        first.setId(1001L);
        StoreDO second = StoreConverter.toDO(createDTO());
        second.setId(1002L);
        second.setName("静安店");

        List<StoreSummaryVO> summaries = StoreConverter.toSummaryVOList(Arrays.asList(first, second));

        assertEquals(2, summaries.size());
        assertEquals(1001L, summaries.get(0).getId());
        assertEquals("徐汇店", summaries.get(0).getName());
        assertEquals("静安店", summaries.get(1).getName());
        assertFalse(hasField(StoreSummaryVO.class, "storeType"));
    }

    private StoreCreateDTO createDTO()
    {
        StoreCreateDTO dto = new StoreCreateDTO();
        dto.setName(" 徐汇店 ");
        dto.setStoreType(1);
        dto.setRegionCode(" 310104 ");
        dto.setPhone("021-12345678");
        dto.setBusinessHours("周一至周日 09:00-22:00");
        dto.setAddress("漕溪北路 88 号");
        dto.setImageUrl("https://cdn.example.com/store/1.jpg");
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
