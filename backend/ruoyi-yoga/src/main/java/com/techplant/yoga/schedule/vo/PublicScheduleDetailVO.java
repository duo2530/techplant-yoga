package com.techplant.yoga.schedule.vo;

import java.util.ArrayList;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 用户端排课详情。 */
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

    @ApiModelProperty(value = "门店地址（关联门店已删除时为空）")
    private String storeAddress;

    @ApiModelProperty(value = "最低开课人数（需求里的「开课人数」）", example = "2")
    private Integer minPersons;

    @ApiModelProperty(value = "预约会员显示名（脱敏，最多 10 位）", example = "[\"张*\"]")
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

    public String getStoreAddress()
    {
        return storeAddress;
    }

    public void setStoreAddress(String storeAddress)
    {
        this.storeAddress = storeAddress;
    }

    public List<String> getBookers()
    {
        return bookers;
    }

    public void setBookers(List<String> bookers)
    {
        this.bookers = bookers == null ? new ArrayList<String>() : bookers;
    }
}
