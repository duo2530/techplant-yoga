package com.techplant.yoga.schedule.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.course.dao.CourseDao;
import com.techplant.yoga.course.domain.CourseDO;
import com.techplant.yoga.schedule.convert.ScheduleConverter;
import com.techplant.yoga.schedule.dao.ScheduleDao;
import com.techplant.yoga.schedule.domain.ScheduleDO;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleStatusDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.service.ScheduleBookingService;
import com.techplant.yoga.schedule.service.ScheduleQueryService;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.schedule.vo.ScheduleBookingSnapshot;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;
import com.techplant.yoga.store.dao.StoreDao;
import com.techplant.yoga.store.domain.StoreDO;

/** 排班业务实现，同时承载课程/预约模块需要的排班查询能力。 */
@Service
public class ScheduleServiceImpl implements ScheduleService, ScheduleQueryService, ScheduleBookingService
{
    private final ScheduleDao scheduleDao;
    private final CourseDao courseDao;
    private final StoreDao storeDao;

    public ScheduleServiceImpl(ScheduleDao scheduleDao, CourseDao courseDao, StoreDao storeDao)
    {
        this.scheduleDao = scheduleDao;
        this.courseDao = courseDao;
        this.storeDao = storeDao;
    }

    @Override
    public PageResult<ScheduleListItemVO> page(ScheduleQuery query)
    {
        IPage<ScheduleDO> page = scheduleDao.selectPage(query);
        java.util.List<ScheduleListItemVO> list = new java.util.ArrayList<ScheduleListItemVO>();
        for (ScheduleDO schedule : page.getRecords())
        {
            list.add(ScheduleConverter.toListItemVO(schedule));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    @Override
    public ScheduleDetailVO getById(Long scheduleId)
    {
        return ScheduleConverter.toDetailVO(requireSchedule(scheduleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleDetailVO create(ScheduleCreateDTO dto)
    {
        validateTime(dto.getStartTime(), dto.getEndTime());
        validateResources(dto.getStoreId(), dto.getCourseId());
        if (scheduleDao.existsSameSlot(dto.getStoreId(), dto.getCourseId(), dto.getStartTime(), dto.getEndTime(), null))
        {
            throw new ServiceException("同一门店的课程时间段已存在排班", 409);
        }
        ScheduleDO schedule = ScheduleConverter.toDO(dto);
        if (scheduleDao.insert(schedule) != 1 || schedule.getId() == null)
        {
            throw new ServiceException("新增排班失败", 500);
        }
        return ScheduleConverter.toDetailVO(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleDetailVO update(Long scheduleId, ScheduleUpdateDTO dto)
    {
        ScheduleDO schedule = requireSchedule(scheduleId);
        validateTime(dto.getStartTime(), dto.getEndTime());
        validateResources(dto.getStoreId(), dto.getCourseId());
        int booked = schedule.getBookingCount() == null ? 0 : schedule.getBookingCount();
        if (booked > 0 && (!dto.getStoreId().equals(schedule.getStoreId()) || !dto.getCourseId().equals(schedule.getCourseId())))
        {
            throw new ServiceException("已有预约的排班不能修改门店或课程", 409);
        }
        if (dto.getCapacity() < booked)
        {
            throw new ServiceException("排班容量不能小于已预约人数", 409);
        }
        if (scheduleDao.existsSameSlot(dto.getStoreId(), dto.getCourseId(), dto.getStartTime(), dto.getEndTime(), scheduleId))
        {
            throw new ServiceException("同一门店的课程时间段已存在排班", 409);
        }
        ScheduleConverter.applyUpdate(schedule, dto);
        schedule.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        scheduleDao.updateById(schedule);
        return getById(scheduleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long scheduleId, ScheduleStatusDTO dto)
    {
        requireSchedule(scheduleId);
        scheduleDao.updateStatus(scheduleId, dto.getStatus(), CurrentUserUtils.getUserIdOrNull());
    }

    @Override
    public long countUnfinishedByCourseId(Long courseId)
    {
        return scheduleDao.countUnfinishedByCourseId(courseId, LocalDateTime.now());
    }

    @Override
    public long countByCourseId(Long courseId)
    {
        return scheduleDao.countByCourseId(courseId);
    }

    @Override
    public ScheduleBookingSnapshot lockForBooking(Long scheduleId)
    {
        ScheduleDO schedule = scheduleDao.selectForUpdate(scheduleId);
        if (schedule == null)
        {
            throw new ServiceException("排班不存在或已删除", 404);
        }
        if (schedule.getStatus() == null || schedule.getStatus() != 1 || schedule.getEndTime().compareTo(LocalDateTime.now()) <= 0)
        {
            throw new ServiceException("该排班当前不可预约", 409);
        }
        validateResources(schedule.getStoreId(), schedule.getCourseId());
        return ScheduleConverter.toBookingSnapshot(schedule);
    }

    @Override
    public void updateBookingCount(Long scheduleId, Integer bookingCount)
    {
        if (scheduleDao.updateBookingCount(scheduleId, bookingCount) != 1)
        {
            throw new ServiceException("更新排班预约人数失败", 500);
        }
    }

    private ScheduleDO requireSchedule(Long scheduleId)
    {
        ScheduleDO schedule = scheduleDao.selectById(scheduleId);
        if (schedule == null)
        {
            throw new ServiceException("排班不存在或已删除", 404);
        }
        return schedule;
    }

    private void validateTime(LocalDateTime startTime, LocalDateTime endTime)
    {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime))
        {
            throw new ServiceException("排班开始时间必须早于结束时间", 400);
        }
    }

    private void validateResources(Long storeId, Long courseId)
    {
        StoreDO store = storeDao.selectById(storeId);
        if (store == null || store.getStatus() == null || store.getStatus() != 1)
        {
            throw new ServiceException("门店不存在或已停用", 409);
        }
        CourseDO course = courseDao.selectById(courseId);
        if (course == null || course.getStatus() == null || course.getStatus() != 1)
        {
            throw new ServiceException("课程不存在或已停用", 409);
        }
        if (course.getStoreId() == null || !storeId.equals(course.getStoreId()))
        {
            throw new ServiceException("排班门店必须与课程所属门店一致", 409);
        }
    }
}
