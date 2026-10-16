package com.techplant.yoga.store.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 门店数据对象，与 {@code t_store} 一一对应。 */
@TableName("t_store")
@Getter
@Setter
public class StoreDO {
    /** 门店ID（雪花ID，应用侧生成） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店编号（服务端分配的四位数字，创建后不可修改，uk_store_no） */
    private String storeNo;

    /** 门店名称（全平台唯一，uk_store_name） */
    private String name;

    /** 门店类型：1主力店 2精品店 */
    private Integer storeType;

    /** 所在区域：上海市市辖区行政区划代码（字典 store_region） */
    private String regionCode;

    /** 联系电话（文本，允许座机／手机号／分机） */
    private String phone;

    /** 营业时间（一段展示文本，不拆分时段） */
    private String businessHours;

    /** 地址 */
    private String address;

    /** 门店图片 URL（单图，可空） */
    private String imageUrl;

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
}
