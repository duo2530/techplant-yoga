package com.techplant.yoga.common.response;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果结构（详细设计 §2.3.2）。
 *
 * <p>service 层把 MyBatis-Plus 的 {@code IPage} 转换成该对象，**不把 IPage 暴露给前端**（§2.1.4）。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T>
{
    /** 符合条件的总记录数 */
    private Long total;

    /** 当前页码，从 1 开始 */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;

    /** 当前页数据 */
    private List<T> list;

    public PageResult()
    {
    }

    public PageResult(Long total, Integer pageNum, Integer pageSize, List<T> list)
    {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list == null ? new ArrayList<T>() : list;
    }

    /**
     * 组装分页结果
     */
    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> list)
    {
        return new PageResult<T>(total, (int) pageNum, (int) pageSize, list);
    }

    public Long getTotal()
    {
        return total;
    }

    public void setTotal(Long total)
    {
        this.total = total;
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }

    public List<T> getList()
    {
        return list;
    }

    public void setList(List<T> list)
    {
        this.list = list;
    }
}
