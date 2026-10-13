package com.techplant.yoga.classroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techplant.yoga.classroom.domain.ClassroomDO;

/**
 * 教室 Mapper。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}：单表 CRUD、雪花ID、分页都由框架提供；
 * 复杂 SQL 才写 XML。扫描路径见 {@code com.techplant.yoga.common.config.MybatisPlusConfig} 的 {@code @MapperScan}。</p>
 */
public interface ClassroomMapper extends BaseMapper<ClassroomDO>
{
}
