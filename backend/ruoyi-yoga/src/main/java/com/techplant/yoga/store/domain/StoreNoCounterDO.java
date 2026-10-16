package com.techplant.yoga.store.domain;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 门店编号计数器数据对象，与 {@code t_store_no_counter} 一一对应（表内只有固定 id = 1 的一行）。 */
@TableName("t_store_no_counter")
@Getter
@Setter
public class StoreNoCounterDO {
    /** 固定为 1（由 SQL 初始化，不参与主键生成） */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /** 已分配的最大门店编号 */
    private Integer lastNo;
}
