package com.techplant.yoga.reservation.service;

import java.util.List;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.reservation.dto.ReservationCreateDTO;
import com.techplant.yoga.reservation.query.ReservationAdminQuery;
import com.techplant.yoga.reservation.query.ReservationPublicQuery;
import com.techplant.yoga.reservation.vo.ReservationAdminItemVO;
import com.techplant.yoga.reservation.vo.ReservationPublicItemVO;

/** 预约业务：用户端预约课程、取消预约、预约签到与我的预约查询，以及管理端预约列表查询。 */
public interface ReservationService {
    /**
     * 预约课程：扣次、占位与预约写入在同一事务内
     *
     * <p>同一个 {@code requestId} 且参数一致的提交按成功返回，不重复创建预约、占位或扣次；
     * 参数不同返回冲突。</p>
     *
     * @param memberId 登录会员 ID，由登录态取得
     * @throws com.ruoyi.common.exception.ServiceException 排课不存在／待上架、会员卡不存在或不属于本人、
     *         关联资料已删除（404）；不可预约、人数超限、卡不可用或不适用、重复预约、时间冲突、
     *         幂等参数冲突（409）；参数格式非法（500）
     */
    void create(Long memberId, ReservationCreateDTO dto);

    /**
     * 取消预约：释放名额并返次在同一事务内；已取消的重复请求按成功返回
     *
     * @throws com.ruoyi.common.exception.ServiceException 预约不存在或不属于本人（404）；
     *         超过取消截止时间、已签到、排课已取消（409）
     */
    void cancel(Long memberId, Long reservationId);

    /**
     * 预约签到：不重复扣次，已有签到记录的重复请求按成功返回
     *
     * @throws com.ruoyi.common.exception.ServiceException 预约不存在或不属于本人（404）；
     *         未到签到窗口、课程已结束、已取消或排课已取消（409）
     */
    void checkIn(Long memberId, Long reservationId);

    /**
     * 我的预约分页：固定按登录会员筛选，无记录或页码超界返回空列表
     *
     * @throws com.ruoyi.common.exception.ServiceException 状态或卡类型取值非法（500）
     */
    PageResult<ReservationPublicItemVO> pageForMember(Long memberId, ReservationPublicQuery query);

    /**
     * 管理端预约分页：门店、课程与卡类型关联筛选，上课日期按预约开始时间区间筛选；不限制当前门店
     *
     * <p>按排课查询名单同样使用本方法；无记录或页码超界返回空 rows，total 为筛选结果总数。
     * 课程、教练、门店、教室、会员与会员卡的展示字段读取当前资料，关联资料已删除时对应字段为空。</p>
     *
     * @throws com.ruoyi.common.exception.ServiceException 状态、卡类型、日期范围或 ID 非法（500）
     */
    PageResult<ReservationAdminItemVO> pageForAdmin(ReservationAdminQuery query);

    /**
     * 排课详情的脱敏预约人员：未取消预约按预约时间、ID 升序，按会员去重后最多取 10 位
     *
     * @return 姓名只保留首字符的显示名列表；无预约返回空列表
     */
    List<String> bookerDisplayNames(Long scheduleId);
}
