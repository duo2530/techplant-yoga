package com.techplant.yoga.member.dto;

import lombok.Getter;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 修改会员的请求体：只维护性别与等级。
 *
 * <p>省略字段保留原值；显式提交 {@code null} 视为参数非法（由服务层报 400）。
 * 手机号、显示名与登录字段不在本模型内，请求中携带时由 JSON 反序列化直接忽略。</p>
 */
@ApiModel("修改会员请求")
public class MemberUpdateDTO {
    @ApiModelProperty(value = "性别：0 未知 1 男 2 女；省略保留原值", example = "1")
    @Getter
    private Integer gender;

    @ApiModelProperty(value = "会员等级；本期仅支持 0（V0 普通会员）；省略保留原值", example = "0")
    @Getter
    private Integer level;

    /** 请求体是否显式提交了 gender（提交 null 也算提交） */
    private boolean genderPresent;

    /** 请求体是否显式提交了 level（提交 null 也算提交） */
    private boolean levelPresent;

    public void setGender(Integer gender) {
        this.gender = gender;
        this.genderPresent = true;
    }

    public void setLevel(Integer level) {
        this.level = level;
        this.levelPresent = true;
    }

    /**
     * 是否提交了性别：字段缺失返回 {@code false}，提交 {@code null} 返回 {@code true}
     */
    public boolean genderPresent() {
        return genderPresent;
    }

    /**
     * 是否提交了会员等级：字段缺失返回 {@code false}，提交 {@code null} 返回 {@code true}
     */
    public boolean levelPresent() {
        return levelPresent;
    }
}
