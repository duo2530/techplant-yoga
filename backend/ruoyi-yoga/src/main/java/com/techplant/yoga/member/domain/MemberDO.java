package com.techplant.yoga.member.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 会员与登录信息数据对象，与 {@code t_member} 一一对应。 */
@TableName("t_member")
@Getter
@Setter
public class MemberDO {
    /** 会员ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信 openid */
    private String wechatOpenid;

    /** 微信 unionid */
    private String wechatUnionid;

    /** 最后登录时间（仅登录成功更新） */
    private LocalDateTime lastLoginTime;

    /** 手机号（唯一，注册后不可修改） */
    private String phone;

    /** 密码（BCrypt 哈希；为空表示未注册） */
    private String password;

    /** 手机号后 4 位（管理端按后四位检索） */
    private String phoneLast4;

    /** 会员显示名（由系统生成：忻会员 + 手机号后 4 位） */
    private String name;

    /** 性别：0 未知 / 1 男 / 2 女 */
    private Integer gender;

    /** 会员等级 */
    private Integer level;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否已注册：{@code password} 非空即已注册 */
    public boolean isRegistered() {
        return password != null && !password.trim().isEmpty();
    }
}
