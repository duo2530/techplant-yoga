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

/**
 * 门店管理业务服务（门店管理详细设计 §3.2、§3.3）。
 *
 * <p>职责：名称唯一校验、存在性校验、按字典校验所在区域、<b>删除门禁编排</b>（调教室与排课的
 * 统计接口）、事务边界；并<b>对外</b>提供门店存在性查询与批量摘要，供教室模块（校验所属门店、
 * 批量补 storeName）与排课模块（校验门店、批量补名称）使用 —— 全项目统一<b>不另立 QueryService</b>
 * （详细设计总览 §6）。</p>
 *
 * <p>一期门店<b>没有状态字段</b>，因此本接口没有任何状态变更方法（BR-门店-008）。</p>
 */
public interface StoreService
{
    /** 管理端分页查询（门店管理详细设计 §2.2.1） */
    PageResult<StoreListItemVO> page(StoreQuery query);

    /** 用户端分页查询（用户端接口详细设计 §2.1）：regionCode 精确、keyword 同时匹配名称与地址 */
    PageResult<StorePublicItemVO> publicPage(StorePublicQuery query);

    /** 查询门店详情；不存在 → 404「门店不存在或已被删除」（§2.2.2） */
    StoreDetailVO getById(Long storeId);

    /** 新增门店：名称唯一校验；成功不返回新 ID（§2.2.3） */
    void create(StoreCreateDTO dto);

    /** 修改门店（全量编辑）：名称唯一校验排除自身；<b>不做引用检查</b>（§2.2.4） */
    StoreDetailVO update(Long storeId, StoreUpdateDTO dto);

    /** 删除门店（物理删除，带门禁，fail-closed）（§2.2.5） */
    void delete(Long storeId);

    /**
     * 批量查询门店摘要（id + name），供教室模块与排课模块批量补名称
     *
     * @param ids 门店编号集合，可为 null／空
     * @return 命中的门店摘要；入参为空时返回空列表（不返回 null）
     */
    List<StoreSummaryVO> summaries(Collection<Long> ids);
}
