package com.techplant.yoga.classroom.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.classroom.dto.ClassroomCreateDTO;
import com.techplant.yoga.classroom.dto.ClassroomUpdateDTO;
import com.techplant.yoga.classroom.query.ClassroomQuery;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.common.response.PageResult;

/**
 * 教室业务接口（教室管理详细设计 §3.2）。
 *
 * <p>管理端 4 个接口：列表／新增／改名／<b>物理删除（带门禁）</b>；
 * <b>没有详情接口</b>（列表项已含编辑回填所需的全部字段）。</p>
 *
 * <p>同时<b>对外</b>提供三个方法，供门店模块（删除门禁统计）与排课模块（存在性／归属校验、名称补齐）直接依赖
 * ——<b>全项目统一不另立 QueryService</b>（详细设计总览 §6）。</p>
 */
public interface ClassroomService
{
    /**
     * 查询教室列表（分页，按 {@code store_id ASC, name ASC, id DESC} 稳定排序；storeName 批量补齐）
     */
    PageResult<ClassroomListItemVO> page(ClassroomQuery query);

    /**
     * 新增教室（<b>不返回新 ID</b>）
     *
     * @throws com.ruoyi.common.exception.ServiceException 门店不存在（404）、同门店重名（409）
     */
    void create(ClassroomCreateDTO dto);

    /**
     * 修改教室（<b>只改名称</b>，不加排课门禁），成功只返回统一成功响应
     *
     * @throws com.ruoyi.common.exception.ServiceException 教室不存在（404）、同门店重名（409）
     */
    void update(Long classroomId, ClassroomUpdateDTO dto);

    /**
     * 删除教室（<b>物理删除</b>，带排课引用门禁；统计失败 fail-closed）
     *
     * @throws com.ruoyi.common.exception.ServiceException 教室不存在（404）、仍有未结束排课（409）
     */
    void delete(Long classroomId);

    /**
     * 统计某门店名下的教室数量（门店模块删除门禁用）
     */
    long countByStoreId(Long storeId);

    /**
     * 校验教室存在且归属指定门店（排课模块新增／修改排课时用）
     *
     * @throws com.ruoyi.common.exception.ServiceException 教室不存在或已被删除（404）、归属不符（409）
     */
    void validateExistsAndBelongsToStore(Long classroomId, Long storeId);

    /**
     * 批量取教室摘要（跨模块出参；只返回存在的教室，入参 null／空集合返回空列表）
     */
    List<ClassroomSummaryVO> summaries(Collection<Long> ids);
}
