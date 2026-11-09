package com.techplant.yoga.reservation.dao;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.enums.ReservationStatusEnum;
import com.techplant.yoga.reservation.mapper.ReservationMapper;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.schedule.enums.ScheduleStatusEnum;

/** 预约单数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class ReservationDaoImpl implements ReservationDao {
    /** 排队仍可补位：排课已上架且未过停止排队时间；{0} 为已上架状态码，{1} 为当前时间 */
    private static final String QUEUE_OPEN_EXISTS = "EXISTS (SELECT 1 FROM t_schedule s"
            + " WHERE s.id = t_reservation.schedule_id AND s.status = {0}"
            + " AND t_reservation.start_time >= DATE_ADD({1}, INTERVAL s.queue_stop_minutes MINUTE))";

    private final ReservationMapper reservationMapper;

    @Override
    public ReservationDO selectById(Long reservationId) {
        if (reservationId == null) {
            return null;
        }
        return reservationMapper.selectById(reservationId);
    }

    @Override
    public ReservationDO selectByMemberAndRequestId(Long memberId, String requestId) {
        if (memberId == null || requestId == null) {
            return null;
        }
        return reservationMapper.selectOne(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getMemberId, memberId).eq(ReservationDO::getRequestId, requestId));
    }

    @Override
    public ReservationDO selectByMemberAndRequestIdForUpdate(Long memberId, String requestId) {
        if (memberId == null || requestId == null) {
            return null;
        }
        return reservationMapper
                .selectOne(Wrappers.<ReservationDO>lambdaQuery().eq(ReservationDO::getMemberId, memberId)
                        .eq(ReservationDO::getRequestId, requestId).last("LIMIT 1 FOR UPDATE"));
    }

    @Override
    public ReservationDO selectByIdForUpdate(Long reservationId) {
        if (reservationId == null) {
            return null;
        }
        return reservationMapper.selectOne(
                Wrappers.<ReservationDO>lambdaQuery().eq(ReservationDO::getId, reservationId).last("FOR UPDATE"));
    }

    @Override
    public int insert(ReservationDO reservation) {
        return reservationMapper.insert(reservation);
    }

    @Override
    public IPage<ReservationDO> selectPageForMember(Long memberId, ReservationPublicQuery query,
            Collection<Long> memberCardIds) {
        LambdaQueryWrapper<ReservationDO> wrapper = Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getMemberId, memberId)
                .in(memberCardIds != null, ReservationDO::getMemberCardId, memberCardIds)
                // 稳定排序：最新的预约在前
                .orderByDesc(ReservationDO::getReservationTime).orderByDesc(ReservationDO::getId);
        filterDisplayStatus(wrapper, query.getStatus());
        return reservationMapper.selectPage(new Page<ReservationDO>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    /** 按展示状态筛选：排课不再上架或已过停止排队时间的排队中记录归入排队失败 */
    private void filterDisplayStatus(LambdaQueryWrapper<ReservationDO> wrapper, Integer status) {
        if (status == null) {
            return;
        }
        Integer queued = ReservationStatusEnum.QUEUED.getCode();
        Integer published = ScheduleStatusEnum.PUBLISHED.getCode();
        LocalDateTime now = LocalDateTime.now();
        if (queued.equals(status)) {
            wrapper.eq(ReservationDO::getStatus, queued).apply(QUEUE_OPEN_EXISTS, published, now);
        } else if (ReservationStatusEnum.QUEUE_FAILED.getCode().equals(status)) {
            wrapper.and(w -> w.eq(ReservationDO::getStatus, status).or(o -> o.eq(ReservationDO::getStatus, queued)
                    .apply("NOT " + QUEUE_OPEN_EXISTS, published, now)));
        } else {
            wrapper.eq(ReservationDO::getStatus, status);
        }
    }

    @Override
    public IPage<ReservationDO> selectAdminPage(ReservationAdminQuery query) {
        // 上课日期区间按预约开始时间快照换算：起始日 00:00:00（含）到结束日次日 00:00:00（不含）
        LocalDateTime startTime = query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
        LocalDateTime endTimeExclusive = query.getEndDate() == null
                ? null
                : query.getEndDate().plusDays(1).atStartOfDay();
        return reservationMapper.selectAdminPage(new Page<ReservationDO>(query.getPageNum(), query.getPageSize()),
                query, startTime, endTimeExclusive, LocalDateTime.now());
    }

    @Override
    public ReservationDO selectOpenByMemberAndSchedule(Long memberId, Long scheduleId) {
        return reservationMapper.selectOne(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getMemberId, memberId).eq(ReservationDO::getScheduleId, scheduleId)
                .in(ReservationDO::getStatus, ReservationStatusEnum.OPEN_CODES).last("LIMIT 1 FOR UPDATE"));
    }

    @Override
    public int cancel(Long reservationId, Integer fromStatus, LocalDateTime cancelTime, Integer cancelReason) {
        return reservationMapper.update(null,
                Wrappers.<ReservationDO>lambdaUpdate().eq(ReservationDO::getId, reservationId)
                        .eq(ReservationDO::getStatus, fromStatus)
                        .set(ReservationDO::getStatus, ReservationStatusEnum.CANCELLED.getCode())
                        .set(ReservationDO::getCancelTime, cancelTime).set(ReservationDO::getCancelReason, cancelReason)
                        .set(ReservationDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int checkIn(Long reservationId, LocalDateTime checkInTime) {
        return reservationMapper.update(null,
                Wrappers.<ReservationDO>lambdaUpdate().eq(ReservationDO::getId, reservationId)
                        .eq(ReservationDO::getStatus, ReservationStatusEnum.RESERVED.getCode())
                        .set(ReservationDO::getStatus, ReservationStatusEnum.CHECKED_IN.getCode())
                        .set(ReservationDO::getCheckInTime, checkInTime)
                        .set(ReservationDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<ReservationDO> selectSeatedBySchedule(Long scheduleId) {
        return reservationMapper
                .selectList(Wrappers.<ReservationDO>lambdaQuery().eq(ReservationDO::getScheduleId, scheduleId)
                        .in(ReservationDO::getStatus, ReservationStatusEnum.SEAT_CODES)
                        .orderByAsc(ReservationDO::getReservationTime).orderByAsc(ReservationDO::getId));
    }

    @Override
    public ReservationDO selectFirstQueuedForUpdate(Long scheduleId) {
        return reservationMapper.selectOne(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getScheduleId, scheduleId)
                .eq(ReservationDO::getStatus, ReservationStatusEnum.QUEUED.getCode())
                .orderByAsc(ReservationDO::getReservationTime).orderByAsc(ReservationDO::getId)
                .last("LIMIT 1 FOR UPDATE"));
    }

    @Override
    public int promote(Long reservationId, LocalDateTime reservationTime) {
        return transitFromQueued(reservationId, Wrappers.<ReservationDO>lambdaUpdate()
                .set(ReservationDO::getStatus, ReservationStatusEnum.RESERVED.getCode())
                .set(ReservationDO::getReservationTime, reservationTime));
    }

    @Override
    public int markQueueFailed(Long reservationId) {
        return transitFromQueued(reservationId, Wrappers.<ReservationDO>lambdaUpdate()
                .set(ReservationDO::getStatus, ReservationStatusEnum.QUEUE_FAILED.getCode()));
    }

    private int transitFromQueued(Long reservationId, LambdaUpdateWrapper<ReservationDO> update) {
        return reservationMapper.update(null, update.eq(ReservationDO::getId, reservationId)
                .eq(ReservationDO::getStatus, ReservationStatusEnum.QUEUED.getCode())
                .set(ReservationDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public long countQueued(Long scheduleId) {
        return reservationMapper.selectCount(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getScheduleId, scheduleId)
                .eq(ReservationDO::getStatus, ReservationStatusEnum.QUEUED.getCode()));
    }

    @Override
    public long countQueuedForUpdate(Long scheduleId) {
        return reservationMapper.selectCount(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getScheduleId, scheduleId)
                .eq(ReservationDO::getStatus, ReservationStatusEnum.QUEUED.getCode()).last("FOR UPDATE"));
    }

    @Override
    public long countQueuedBefore(Long scheduleId, LocalDateTime reservationTime, Long reservationId) {
        return reservationMapper.selectCount(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getScheduleId, scheduleId)
                .eq(ReservationDO::getStatus, ReservationStatusEnum.QUEUED.getCode())
                .and(w -> w.lt(ReservationDO::getReservationTime, reservationTime).or(
                        o -> o.eq(ReservationDO::getReservationTime, reservationTime).lt(ReservationDO::getId,
                                reservationId))));
    }
}
