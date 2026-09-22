package com.techplant.yoga.common.response;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果结构（详细设计 §2.3.2）。
 *
 * <p><b>这是 service 层内部结构，不是 HTTP 契约：</b>接口返回给前端的列表数据按若依标准用
 * {@code com.ruoyi.common.core.page.TableDataInfo}（{@code {total, rows, code, msg}}），
 * controller 负责把本对象装配成 {@code TableDataInfo}（2026-09-22 响应体系决策）。
 * service 内部用它承载「总记录数 + 当前页数据」，避免把 MyBatis-Plus 的 {@code IPage} 暴露出去（§2.1.4）。</p>
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
