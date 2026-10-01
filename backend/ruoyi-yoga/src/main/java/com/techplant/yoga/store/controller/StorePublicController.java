package com.techplant.yoga.store.controller;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 用户端门店列表接口（用户端接口详细设计 §2.1）。
 *
 * <p>免登录：类上标 {@link Anonymous}，由 {@code PermitAllUrlProperties} 收进 permitAll 白名单。
 * 只有 GET，一期用户端没有任何写操作。</p>
 *
 * <p>返回分页 {@link TableDataInfo}，{@code rows} 为 {@code StorePublicItemVO}：
 * 支持按区域筛选（regionCode 精确）与关键字搜索（keyword 同时模糊匹配门店名称与地址），
 * 排序 {@code region_code ASC, name ASC, id DESC}；<b>不返回审计字段与任何状态字段</b>。</p>
 */
@Api(tags = "用户端-门店")
@Anonymous
@RestController
@RequestMapping("/api/stores")
public class StorePublicController extends BaseController
{
    private final StoreService storeService;

    public StorePublicController(StoreService storeService)
    {
        this.storeService = storeService;
    }

    /** 用户端查询门店列表（分页，免登录） */
    @ApiOperation("用户端查询门店列表")
    @GetMapping
    public TableDataInfo list(@Valid StorePublicQuery query)
    {
        PageResult<StorePublicItemVO> page = storeService.publicPage(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
