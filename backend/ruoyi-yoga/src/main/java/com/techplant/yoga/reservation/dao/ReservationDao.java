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
     * 该会员在该排课上是否已有未取消预约（status &lt;&gt; 2）
     *
     * <p>当前读：取锁后的重复预约校验必须看到并发提交的最新预约，不能被事务快照挡住。</p>
     */
    boolean existsActiveByMemberAndSchedule(Long memberId, Long scheduleId);

    /**
     * 该会员在给定时间区间内是否已有未取消预约（status &lt;&gt; 2 且区间相交）
     *
     * <p>当前读：取锁后的时间冲突校验必须看到并发提交的最新预约，不能被事务快照挡住。</p>
     */
    boolean existsTimeConflict(Long memberId, LocalDateTime startTime, LocalDateTime endTime);

    /** 取消：仅当仍为已预约时写入取消时间与原因 */
    int cancel(Long reservationId, LocalDateTime cancelTime, Integer cancelReason);

    /** 签到：仅当仍为已预约时写入签到时间与已签到状态 */
    int checkIn(Long reservationId, LocalDateTime checkInTime);

    /** 该排课的全部未取消预约（按预约时间、ID 升序），用于排课详情的脱敏预约人员 */
    List<ReservationDO> selectActiveBySchedule(Long scheduleId);
}
