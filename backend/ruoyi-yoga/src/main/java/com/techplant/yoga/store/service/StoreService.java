package com.techplant.yoga.store.service;

import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;

/** 门店管理业务服务。 */
public interface StoreService
{
    PageResult<StoreListItemVO> page(StoreQuery query);
    List<StorePublicItemVO> publicList();
    StoreDetailVO getById(Long storeId);
    void create(StoreCreateDTO dto);
    StoreDetailVO update(Long storeId, StoreUpdateDTO dto);
    void updateStatus(Long storeId, Integer status);
}
