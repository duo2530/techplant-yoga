package com.techplant.yoga.store.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.enums.StoreTypeEnum;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 门店对象转换（门店管理详细设计 §3.2）。
 *
 * <p>转换是<b>纯函数</b>：不查库、不查字典。区名由 service 按字典 {@code store_region} 查好后
 * 以 {@code regionName} 入参传入，门店类型名由 {@link StoreTypeEnum} 翻译（成对返回，
 * 详细设计总览 §2）。所有请求字段在转换时去掉前后空格（§1.2.3 第 1 条）。</p>
 */
public final class StoreConverter
{
    private StoreConverter() { }

    /** 新增请求 → DO（主键与审计字段由框架填充） */
    public static StoreDO toDO(StoreCreateDTO dto)
    {
        StoreDO store = new StoreDO();
        store.setName(trim(dto.getName()));
        store.setStoreType(dto.getStoreType());
        store.setRegionCode(trim(dto.getRegionCode()));
        store.setPhone(trim(dto.getPhone()));
        store.setBusinessHours(trim(dto.getBusinessHours()));
        store.setAddress(trim(dto.getAddress()));
        store.setImageUrl(trim(dto.getImageUrl()));
        return store;
    }

    /** 修改请求覆盖到 DO（全量编辑） */
    public static void applyUpdate(StoreDO store, StoreUpdateDTO dto)
    {
        store.setName(trim(dto.getName()));
        store.setStoreType(dto.getStoreType());
        store.setRegionCode(trim(dto.getRegionCode()));
        store.setPhone(trim(dto.getPhone()));
        store.setBusinessHours(trim(dto.getBusinessHours()));
        store.setAddress(trim(dto.getAddress()));
        store.setImageUrl(trim(dto.getImageUrl()));
    }

    /** 单行 → 管理端列表项，{@code regionName} 由 service 按字典翻译后传入（可空 → 空字符串） */
    public static StoreListItemVO toListItemVO(StoreDO store, String regionName)
    {
        StoreListItemVO vo = new StoreListItemVO();
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setStoreType(store.getStoreType());
        // 枚举成对返回：storeType + storeTypeName（前端不维护码 → 名映射）
        vo.setStoreTypeName(StoreTypeEnum.nameOf(store.getStoreType()));
        vo.setRegionCode(store.getRegionCode());
        vo.setRegionName(nullToEmpty(regionName));
        vo.setPhone(store.getPhone());
        vo.setBusinessHours(store.getBusinessHours());
        vo.setAddress(store.getAddress());
        vo.setImageUrl(store.getImageUrl());
        vo.setCreateTime(store.getCreateTime());
        vo.setUpdateTime(store.getUpdateTime());
        return vo;
    }

    /** 批量 → 管理端列表项，区名按 {@code regionCode} 从字典映射里取 */
    public static List<StoreListItemVO> toListItemVOList(List<StoreDO> stores, Map<String, String> regionLabelMap)
    {
        List<StoreListItemVO> result = new ArrayList<StoreListItemVO>();
        if (stores != null)
        {
            for (StoreDO store : stores)
            {
                result.add(toListItemVO(store, regionNameOf(regionLabelMap, store.getRegionCode())));
            }
        }
        return result;
    }

    /** 单行 → 管理端详情 */
    public static StoreDetailVO toDetailVO(StoreDO store, String regionName)
    {
        StoreDetailVO vo = new StoreDetailVO();
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setStoreType(store.getStoreType());
        vo.setStoreTypeName(StoreTypeEnum.nameOf(store.getStoreType()));
        vo.setRegionCode(store.getRegionCode());
        vo.setRegionName(nullToEmpty(regionName));
        vo.setPhone(store.getPhone());
        vo.setBusinessHours(store.getBusinessHours());
        vo.setAddress(store.getAddress());
        vo.setImageUrl(store.getImageUrl());
        vo.setCreateTime(store.getCreateTime());
        vo.setUpdateTime(store.getUpdateTime());
        return vo;
    }

    /** 单行 → 用户端门店项（不返回审计字段；imageUrl 为空时给空字符串） */
    public static StorePublicItemVO toPublicItemVO(StoreDO store, String regionName)
    {
        StorePublicItemVO vo = new StorePublicItemVO();
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setStoreType(store.getStoreType());
        vo.setStoreTypeName(StoreTypeEnum.nameOf(store.getStoreType()));
        vo.setRegionCode(store.getRegionCode());
        vo.setRegionName(nullToEmpty(regionName));
        vo.setPhone(store.getPhone());
        vo.setBusinessHours(store.getBusinessHours());
        vo.setAddress(store.getAddress());
        vo.setImageUrl(nullToEmpty(store.getImageUrl()));
        return vo;
    }

    /** 批量 → 用户端门店项 */
    public static List<StorePublicItemVO> toPublicItemVOList(List<StoreDO> stores, Map<String, String> regionLabelMap)
    {
        List<StorePublicItemVO> result = new ArrayList<StorePublicItemVO>();
        if (stores != null)
        {
            for (StoreDO store : stores)
            {
                result.add(toPublicItemVO(store, regionNameOf(regionLabelMap, store.getRegionCode())));
            }
        }
        return result;
    }

    /** 批量 → 门店摘要（跨模块 summaries，只给 id 与 name） */
    public static List<StoreSummaryVO> toSummaryVOList(List<StoreDO> stores)
    {
        List<StoreSummaryVO> result = new ArrayList<StoreSummaryVO>();
        if (stores != null)
        {
            for (StoreDO store : stores)
            {
                result.add(new StoreSummaryVO(store.getId(), store.getName()));
            }
        }
        return result;
    }

    /** 按字典映射翻译区名；查不到返回空字符串（不返回 null） */
    public static String regionNameOf(Map<String, String> regionLabelMap, String regionCode)
    {
        if (regionLabelMap == null || regionCode == null)
        {
            return "";
        }
        String regionName = regionLabelMap.get(regionCode);
        return regionName == null ? "" : regionName;
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
