package com.techplant.yoga.common.mybatis;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.techplant.yoga.common.util.CurrentUserUtils;

/** 审计字段自动填充。 */
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
