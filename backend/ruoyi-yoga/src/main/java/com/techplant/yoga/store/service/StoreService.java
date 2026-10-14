package com.techplant.yoga.store.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/** 门店管理业务服务。 */
public interface StoreService
{
    /** 管理端分页查询 */
    PageResult<StoreListItemVO> page(StoreQuery query);

    /** 用户端分页查询：regionCode 精确、keyword 同时匹配名称与地址 */
    PageResult<StorePublicItemVO> publicPage(StorePublicQuery query);

    /** 查询门店详情；不存在 → 404「门店不存在或已被删除」 */
    StoreDetailVO getById(Long storeId);

    /** 新增门店：名称唯一校验；成功不返回新 ID */
    /**
     * 新增门店：名称唯一校验；门店编号在事务内从编号计数器分配；成功不返回新 ID
     *
     * @throws com.ruoyi.common.exception.ServiceException 名称重复（409）、编号已用尽或冲突（409）、取值非法（500）
     */
    void create(StoreCreateDTO dto);

    /** 修改门店（全量编辑）：名称唯一校验排除自身，门店编号保持不变；<b>不做引用检查</b> */
    StoreDetailVO update(Long storeId, StoreUpdateDTO dto);

    /** 删除门店（物理删除，带门禁，fail-closed）；门店编号不回收 */
    void delete(Long storeId);

    /**
     * 批量查询门店摘要（id + 门店编号 + name），供教室、排课与会员卡模块批量补名称
     *
     * @param ids 门店 ID 集合，可为 null／空
     * @return 命中的门店摘要；入参为空时返回空列表（不返回 null）
     */
    List<StoreSummaryVO> summaries(Collection<Long> ids);
}
