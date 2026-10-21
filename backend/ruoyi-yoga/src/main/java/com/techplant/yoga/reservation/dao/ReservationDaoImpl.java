package com.techplant.yoga.reservation.dao;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.enums.ReservationStatusEnum;
import com.techplant.yoga.reservation.mapper.ReservationMapper;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;

/** 预约单数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class ReservationDaoImpl implements ReservationDao {
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
                .eq(query.getStatus() != null, ReservationDO::getStatus, query.getStatus())
                .in(memberCardIds != null, ReservationDO::getMemberCardId, memberCardIds)
                // 稳定排序：最新的预约在前
                .orderByDesc(ReservationDO::getReservationTime).orderByDesc(ReservationDO::getId);
        return reservationMapper.selectPage(new Page<ReservationDO>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Override
    public IPage<ReservationDO> selectAdminPage(ReservationAdminQuery query) {
        // 上课日期区间按预约开始时间快照换算：起始日 00:00:00（含）到结束日次日 00:00:00（不含）
        LocalDateTime startTime = query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
        LocalDateTime endTimeExclusive = query.getEndDate() == null
                ? null
                : query.getEndDate().plusDays(1).atStartOfDay();
        return reservationMapper.selectAdminPage(new Page<ReservationDO>(query.getPageNum(), query.getPageSize()),
                query, startTime, endTimeExclusive);
    }

    @Override
    public boolean existsActiveByMemberAndSchedule(Long memberId, Long scheduleId) {
        // 当前读（FOR UPDATE）：必须看到并发事务已提交的同排课预约
        return reservationMapper.selectOne(Wrappers.<ReservationDO>lambdaQuery()
                .eq(ReservationDO::getMemberId, memberId).eq(ReservationDO::getScheduleId, scheduleId)
                .ne(ReservationDO::getStatus, ReservationStatusEnum.CANCELLED.getCode())
                .last("LIMIT 1 FOR UPDATE")) != null;
    }

    @Override
    public int cancel(Long reservationId, LocalDateTime cancelTime, Integer cancelReason) {
        return reservationMapper.update(null,
                Wrappers.<ReservationDO>lambdaUpdate().eq(ReservationDO::getId, reservationId)
                        .eq(ReservationDO::getStatus, ReservationStatusEnum.RESERVED.getCode())
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
    public List<ReservationDO> selectActiveBySchedule(Long scheduleId) {
        return reservationMapper
                .selectList(Wrappers.<ReservationDO>lambdaQuery().eq(ReservationDO::getScheduleId, scheduleId)
                        .ne(ReservationDO::getStatus, ReservationStatusEnum.CANCELLED.getCode())
                        .orderByAsc(ReservationDO::getReservationTime).orderByAsc(ReservationDO::getId));
    }
}
