package com.techplant.yoga.common.mybatis;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.techplant.yoga.common.util.CurrentUserUtils;

/**
 * 审计字段自动填充（详细设计 §1.1.1「审计字段」、§3.1.4）。
 *
 * <p>由 MyBatis-Plus 的 {@link MetaObjectHandler} 在插入/更新时填充
 * {@code create_by / create_time / update_by / update_time}；数据库的
 * {@code DEFAULT CURRENT_TIMESTAMP} 只作兜底。</p>
 *
 * <p>未登录（拿不到操作人）时只填时间、不填操作人：审计字段在表里本来就是可空的，
 * 不应因为取不到登录人而让写操作失败。</p>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler
{
    @Override
    public void insertFill(MetaObject metaObject)
    {
        LocalDateTime now = LocalDateTime.now();
        Long operator = CurrentUserUtils.getUserIdOrNull();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", Long.class, operator);
        strictInsertFill(metaObject, "updateBy", Long.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject)
    {
        LocalDateTime now = LocalDateTime.now();
        Long operator = CurrentUserUtils.getUserIdOrNull();
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updateBy", Long.class, operator);
    }
}
