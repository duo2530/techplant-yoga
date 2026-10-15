package com.techplant.yoga.reservation.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;

/** 预约单 Mapper。 */
public interface ReservationMapper extends BaseMapper<ReservationDO>
{
    /**
     * 管理端分页查询：门店与课程经排课关联筛选，卡类型经会员卡关联筛选，其余条件按预约字段筛选
     *
     * @param page             分页参数
     * @param query            筛选条件
     * @param startTime        起始时间（含），按预约 start_time 判定；为空不筛
     * @param endTimeExclusive 结束时间（不含，即结束日期次日 00:00:00）；为空不筛
     */
    IPage<ReservationDO> selectAdminPage(IPage<ReservationDO> page, @Param("query") ReservationAdminQuery query,
            @Param("startTime") LocalDateTime startTime, @Param("endTimeExclusive") LocalDateTime endTimeExclusive);
}
