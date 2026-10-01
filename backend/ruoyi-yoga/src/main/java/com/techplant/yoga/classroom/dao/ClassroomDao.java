package com.techplant.yoga.classroom.dao;

import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.query.ClassroomQuery;

/**
 * 教室数据访问（教室管理详细设计 §3.2）。
 *
 * <p><b>物理删除：</b>{@code ClassroomDO} 没有 {@code deleted} 字段、没有 {@code @TableLogic}，
 * 业务代码也<b>不要</b>拼 {@code deleted = 0} 条件。</p>
 */
public interface ClassroomDao
{
    /**
     * 分页查询教室列表，按 {@code store_id ASC, name ASC, id DESC} 稳定排序
     */
    IPage<ClassroomDO> selectPage(ClassroomQuery query);

    /**
     * 按编号查询教室；不存在返回 {@code null}
     */
    ClassroomDO selectById(Long id);

    /**
     * 按「门店 + 名称」查询教室（同门店内名称唯一性校验）
     *
     * @param storeId 所属门店编号
     * @param name    教室名称
     */
    ClassroomDO selectByStoreIdAndName(Long storeId, String name);

    /**
     * 按「门店 + 名称」查询教室并排除自身（修改时的查重）
     *
     * @param excludeId 需要排除的教室编号
     */
    ClassroomDO selectByStoreIdAndNameExcludeId(Long storeId, String name, Long excludeId);

    /**
     * 按编号批量查询（供排课模块批量补齐教室名称；调用方保证非空集合）
     */
    List<ClassroomDO> selectByIds(Collection<Long> ids);

    /**
     * 新增教室（主键不传，由 MyBatis-Plus 生成雪花ID）
     *
     * @return 影响行数
     */
    int insert(ClassroomDO classroom);

    /**
     * <b>只更新名称</b>（显式 SET name）：不整对象覆盖，避免把 {@code store_id} 写空
     * （教室管理详细设计 §3.4 第 2 条）
     *
     * @return 影响行数
     */
    int updateName(Long id, String name, Long updateBy);

    /**
     * 按编号<b>物理删除</b>教室
     *
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 统计某门店名下的教室数量（供门店模块删除门禁调用）
     */
    long countByStoreId(Long storeId);
}
