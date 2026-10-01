package com.techplant.yoga.schedule.vo;

import java.util.ArrayList;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端排课详情（课程详情页，《用户端接口详细设计》§2.3 / §3.3）。
 *
 * <p>在 {@link ScheduleCardVO} 的基础上补：{@code courseId}、{@code courseIntro}、{@code coachId}、
 * {@code coachAvatarUrl}、{@code coachIntro}、{@code minPersons}（＝需求里的「开课人数」）、{@code bookers}。</p>
 *
 * <p><b>口径</b>：课种取排课快照；封面／介绍／难度实时取课程；{@code bookers} 一期没有预约，
 * **固定返回空数组 {@code []}（不是 null）**；课程／教练／教室已删除时对应名称返回**空字符串**。</p>
 */
@ApiModel("用户端排课详情")
public class PublicScheduleDetailVO extends ScheduleCardVO
{
    @ApiModelProperty(value = "课程ID", example = "1856739201475235801")
    private Long courseId;

    @ApiModelProperty(value = "课程介绍（实时取课程）")
    private String courseIntro;

    @ApiModelProperty(value = "教练ID", example = "1856739201475237001")
    private Long coachId;

    @ApiModelProperty(value = "教练头像URL")
    private String coachAvatarUrl;

    @ApiModelProperty(value = "教练简介")
    private String coachIntro;

    @ApiModelProperty(value = "最低开课人数（需求里的「开课人数」）", example = "2")
    private Integer minPersons;

    @ApiModelProperty(value = "预约人员列表（一期固定空数组）", example = "[]")
    private List<String> bookers = new ArrayList<String>();

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getCourseIntro()
    {
        return courseIntro;
    }

    public void setCourseIntro(String courseIntro)
    {
        this.courseIntro = courseIntro;
    }

    public Long getCoachId()
    {
        return coachId;
    }

    public void setCoachId(Long coachId)
    {
        this.coachId = coachId;
    }

    public String getCoachAvatarUrl()
    {
        return coachAvatarUrl;
    }

    public void setCoachAvatarUrl(String coachAvatarUrl)
    {
        this.coachAvatarUrl = coachAvatarUrl;
    }

    public String getCoachIntro()
    {
        return coachIntro;
    }

    public void setCoachIntro(String coachIntro)
    {
        this.coachIntro = coachIntro;
    }

    public Integer getMinPersons()
    {
        return minPersons;
    }

    public void setMinPersons(Integer minPersons)
    {
        this.minPersons = minPersons;
    }

    public List<String> getBookers()
    {
        return bookers;
    }

    public void setBookers(List<String> bookers)
    {
        // 空值兜底：契约要求固定 []，绝不能是 null
        this.bookers = bookers == null ? new ArrayList<String>() : bookers;
    }
}
