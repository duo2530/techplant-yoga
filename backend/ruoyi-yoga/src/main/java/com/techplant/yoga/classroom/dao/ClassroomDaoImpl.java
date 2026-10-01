package com.techplant.yoga.classroom.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.mapper.ClassroomMapper;
import com.techplant.yoga.classroom.query.ClassroomQuery;

/**
 * 教室数据访问实现（教室管理详细设计 §3.2、§4）。
 */
@Repository
public class ClassroomDaoImpl implements ClassroomDao
{
    private final ClassroomMapper classroomMapper;

    public ClassroomDaoImpl(ClassroomMapper classroomMapper)
    {
        this.classroomMapper = classroomMapper;
    }

    @Override
    public IPage<ClassroomDO> selectPage(ClassroomQuery query)
    {
        LambdaQueryWrapper<ClassroomDO> wrapper = Wrappers.<ClassroomDO>lambdaQuery()
                .select(ClassroomDO::getId, ClassroomDO::getStoreId, ClassroomDO::getName, ClassroomDO::getCreateTime,
                        ClassroomDO::getUpdateTime);
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.like(hasName, ClassroomDO::getName, query.getName());
        wrapper.eq(query.getStoreId() != null, ClassroomDO::getStoreId, query.getStoreId());
        // 稳定排序：store_id 升序 + name 升序 + id 降序兜底（§3.4 第 5 条）
        wrapper.orderByAsc(ClassroomDO::getStoreId);
        wrapper.orderByAsc(ClassroomDO::getName);
        wrapper.orderByDesc(ClassroomDO::getId);

        Page<ClassroomDO> page = new Page<ClassroomDO>(query.pageNumOrDefault(), query.pageSizeOrDefault());
        return classroomMapper.selectPage(page, wrapper);
    }

    @Override
    public ClassroomDO selectById(Long id)
    {
        return classroomMapper.selectById(id);
    }

    @Override
    public ClassroomDO selectByStoreIdAndName(Long storeId, String name)
    {
        return selectByStoreIdAndNameExcludeId(storeId, name, null);
    }

    @Override
    public ClassroomDO selectByStoreIdAndNameExcludeId(Long storeId, String name, Long excludeId)
    {
        LambdaQueryWrapper<ClassroomDO> wrapper = Wrappers.<ClassroomDO>lambdaQuery()
                .eq(ClassroomDO::getStoreId, storeId)
                .eq(ClassroomDO::getName, name)
                .ne(excludeId != null, ClassroomDO::getId, excludeId)
                .last("LIMIT 1");
        return classroomMapper.selectOne(wrapper);
    }

    @Override
    public List<ClassroomDO> selectByIds(Collection<Long> ids)
    {
        return classroomMapper.selectBatchIds(ids);
    }

    @Override
    public int insert(ClassroomDO classroom)
    {
        return classroomMapper.insert(classroom);
    }

    @Override
    public int updateName(Long id, String name, Long updateBy)
    {
        // 显式只 set name（+ 审计）：绝不能整对象覆盖，否则会把 store_id 写空
        // （教室管理详细设计 §3.4 第 2 条）
        return classroomMapper.update(null, Wrappers.<ClassroomDO>lambdaUpdate()
                .eq(ClassroomDO::getId, id)
                .set(ClassroomDO::getName, name)
                .set(ClassroomDO::getUpdateBy, updateBy)
                .set(ClassroomDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long id)
    {
        // 物理删除：ClassroomDO 没有 @TableLogic，生成 DELETE FROM t_classroom WHERE id = ?
        return classroomMapper.deleteById(id);
    }

    @Override
    public long countByStoreId(Long storeId)
    {
        Long count = classroomMapper.selectCount(Wrappers.<ClassroomDO>lambdaQuery()
                .eq(ClassroomDO::getStoreId, storeId));
        return count == null ? 0L : count;
    }
}
