package com.techplant.yoga.store.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;

/** 门店对象转换。 */
public final class StoreConverter
{
    private static final int STATUS_ENABLED = 1;
    private static final int NOT_DELETED = 0;

    private StoreConverter() { }

    public static StoreDO toDO(StoreCreateDTO dto)
    {
        StoreDO store = new StoreDO();
        store.setName(trim(dto.getName()));
        store.setRegion(trim(dto.getRegion()));
        store.setProvinceCode(trim(dto.getProvinceCode()));
        store.setCityCode(trim(dto.getCityCode()));
        store.setDistrictCode(trim(dto.getDistrictCode()));
        store.setAddress(trim(dto.getAddress()));
        store.setPhone(trim(dto.getPhone()));
        store.setBusinessType(dto.getBusinessType());
        store.setStoreType(dto.getStoreType());
        store.setBusinessHours(trim(dto.getBusinessHours()));
        store.setStatus(STATUS_ENABLED);
        store.setDeleted(NOT_DELETED);
        return store;
    }

    public static void applyUpdate(StoreDO store, StoreUpdateDTO dto)
    {
        store.setName(trim(dto.getName()));
        store.setRegion(trim(dto.getRegion()));
        store.setProvinceCode(trim(dto.getProvinceCode()));
        store.setCityCode(trim(dto.getCityCode()));
        store.setDistrictCode(trim(dto.getDistrictCode()));
        store.setAddress(trim(dto.getAddress()));
        store.setPhone(trim(dto.getPhone()));
        store.setBusinessType(dto.getBusinessType());
        store.setStoreType(dto.getStoreType());
        store.setBusinessHours(trim(dto.getBusinessHours()));
    }

    public static StoreListItemVO toListItemVO(StoreDO store)
    {
        StoreListItemVO vo = new StoreListItemVO();
        copyCommon(store, vo);
        vo.setCreateTime(store.getCreateTime());
        vo.setUpdateTime(store.getUpdateTime());
        return vo;
    }

    public static List<StoreListItemVO> toListItemVOList(List<StoreDO> stores)
    {
        List<StoreListItemVO> result = new ArrayList<StoreListItemVO>();
        if (stores != null)
        {
            for (StoreDO store : stores)
            {
                result.add(toListItemVO(store));
            }
        }
        return result;
    }

    public static StorePublicItemVO toPublicItemVO(StoreDO store)
    {
        StorePublicItemVO vo = new StorePublicItemVO();
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setRegion(store.getRegion());
        vo.setProvinceCode(store.getProvinceCode());
        vo.setCityCode(store.getCityCode());
        vo.setDistrictCode(store.getDistrictCode());
        vo.setAddress(store.getAddress());
        vo.setPhone(store.getPhone());
        vo.setBusinessType(store.getBusinessType());
        vo.setStoreType(store.getStoreType());
        vo.setBusinessHours(store.getBusinessHours());
        return vo;
    }

    public static List<StorePublicItemVO> toPublicItemVOList(List<StoreDO> stores)
    {
        List<StorePublicItemVO> result = new ArrayList<StorePublicItemVO>();
        if (stores != null)
        {
            for (StoreDO store : stores)
            {
                result.add(toPublicItemVO(store));
            }
        }
        return result;
    }

    public static StoreDetailVO toDetailVO(StoreDO store)
    {
        StoreDetailVO vo = new StoreDetailVO();
        copyCommon(store, vo);
        vo.setCreateTime(store.getCreateTime());
        vo.setUpdateTime(store.getUpdateTime());
        return vo;
    }

    private static void copyCommon(StoreDO store, StoreListItemVO vo)
    {
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setRegion(store.getRegion());
        vo.setProvinceCode(store.getProvinceCode());
        vo.setCityCode(store.getCityCode());
        vo.setDistrictCode(store.getDistrictCode());
        vo.setAddress(store.getAddress());
        vo.setPhone(store.getPhone());
        vo.setBusinessType(store.getBusinessType());
        vo.setStoreType(store.getStoreType());
        vo.setBusinessHours(store.getBusinessHours());
        vo.setStatus(store.getStatus());
    }

    private static void copyCommon(StoreDO store, StoreDetailVO vo)
    {
        vo.setId(store.getId());
        vo.setName(store.getName());
        vo.setRegion(store.getRegion());
        vo.setProvinceCode(store.getProvinceCode());
        vo.setCityCode(store.getCityCode());
        vo.setDistrictCode(store.getDistrictCode());
        vo.setAddress(store.getAddress());
        vo.setPhone(store.getPhone());
        vo.setBusinessType(store.getBusinessType());
        vo.setStoreType(store.getStoreType());
        vo.setBusinessHours(store.getBusinessHours());
        vo.setStatus(store.getStatus());
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
