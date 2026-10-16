package com.techplant.yoga.common.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果结构。
 *
 * @param <T> 列表元素类型
 */
@Getter
@Setter
public class PageResult<T> {
    /** 符合条件的总记录数 */
    private Long total;

    /** 当前页码，从 1 开始 */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;

    /** 当前页数据 */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(Long total, Integer pageNum, Integer pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list == null ? new ArrayList<T>() : list;
    }

    /**
     * 组装分页结果
     */
    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> list) {
        return new PageResult<T>(total, (int) pageNum, (int) pageSize, list);
    }
}
