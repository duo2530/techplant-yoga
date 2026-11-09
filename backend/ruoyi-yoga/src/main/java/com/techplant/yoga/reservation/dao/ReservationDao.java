package com.techplant.yoga.reservation.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.reservation.domain.ReservationDO;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;

/**
 * 预约单数据访问语义。
 *
 * <p>本表没有 {@code deleted} 列，删除一律物理删除；写操作都带状态条件，影响行数 0 表示并发状态已变更。</p>
 */
public interface ReservationDao {
    /** 按主键查询（快照读） */
    ReservationDO selectById(Long reservationId);

    /** 按会员与请求幂等标识查询：提交重试复用同一条预约 */
    ReservationDO selectByMemberAndRequestId(Long memberId, String requestId);

    /**
     * 按会员与请求幂等标识做当前读（{@code SELECT ... FOR UPDATE}）
     *
     * <p>取锁后的幂等复查必须用当前读：事务快照在读锁之前就已建立，普通查询看不到并发提交的同键预约。</p>
     */
    ReservationDO selectByMemberAndRequestIdForUpdate(Long memberId, String requestId);

    /**
     * 按主键做当前读（{@code SELECT ... FOR UPDATE}）取预约；不存在返回 {@code null}
     *
     * <p>取消与签到必须先锁定该行，再按读到的状态决定是否流转。</p>
     */
    ReservationDO selectByIdForUpdate(Long reservationId);

    /** 新增预约（主键雪花 ID、审计字段由公共机制填充） */
    int insert(ReservationDO reservation);

    /**
     * 我的预约分页：固定按会员筛选，排序 {@code reservation_time DESC, id DESC}
     *
     * @param memberCardIds 关联会员卡 ID 集合；{@code null} 表示不按卡类型筛选（空集合由调用方直接返回空页）
     */
    IPage<ReservationDO> selectPageForMember(Long memberId, ReservationPublicQuery query,
            Collection<Long> memberCardIds);

    /**
     * 管理端分页：门店与课程经排课关联筛选、卡类型经会员卡关联筛选，上课日期按预约开始时间换算为时间区间；
     * 排序 {@code reservation_time DESC, id DESC}
     */
    IPage<ReservationDO> selectAdminPage(ReservationAdminQuery query);

    /**
     * 该会员在该排课上的已预约、已签到或排队中记录（当前读），没有返回 {@code null}
     *
     * <p>取锁后的重复校验必须看到并发提交的最新记录，不能被事务快照挡住。</p>
     */
    ReservationDO selectOpenByMemberAndSchedule(Long memberId, Long scheduleId);

    /** 取消：仅当仍为 {@code fromStatus} 时写入取消状态、时间与原因 */
    int cancel(Long reservationId, Integer fromStatus, LocalDateTime cancelTime, Integer cancelReason);

    /** 签到：仅当仍为已预约时写入签到时间与已签到状态 */
    int checkIn(Long reservationId, LocalDateTime checkInTime);

    /** 该排课占用名额的预约（已预约、已签到，按预约时间、ID 升序），用于排课详情的脱敏预约人员 */
    List<ReservationDO> selectSeatedBySchedule(Long scheduleId);

    /** 该排课排在最前的排队记录（当前读，按 {@code reservation_time, id} 升序），没有返回 {@code null} */
    ReservationDO selectFirstQueuedForUpdate(Long scheduleId);

    /** 补位：仅当仍为排队中时改为已预约，预约时间改为补位时间 */
    int promote(Long reservationId, LocalDateTime reservationTime);

    /** 补位跳过：仅当仍为排队中时改为排队失败 */
    int markQueueFailed(Long reservationId);

    /** 该排课排队中的记录数 */
    long countQueued(Long scheduleId);

    /** 该排课排队中的记录数，当前读：事务快照之后提交的排队也计入 */
    long countQueuedForUpdate(Long scheduleId);

    /** 排在指定排队记录之前的排队中记录数 */
    long countQueuedBefore(Long scheduleId, LocalDateTime reservationTime, Long reservationId);
}
