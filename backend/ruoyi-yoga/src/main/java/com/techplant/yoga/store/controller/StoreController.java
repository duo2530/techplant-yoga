package com.techplant.yoga.store.controller;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端门店管理接口（门店管理详细设计 §2.2）。 */
@Api(tags = "管理端-门店管理")
@Validated
@RestController
@RequestMapping("/admin/stores")
public class StoreController extends BaseController
{
    private final StoreService storeService;

    public StoreController(StoreService storeService)
    {
        this.storeService = storeService;
    }

    /** 查询门店列表，支持名称、区域、经营类型、门店类型和状态筛选。 */
    @ApiOperation("查询门店列表")
    @GetMapping
    public TableDataInfo list(@Valid StoreQuery query)
    {
        PageResult<StoreListItemVO> page = storeService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 查询门店详情。 */
    @ApiOperation("查询门店详情")
    @GetMapping("/{storeId}")
    public AjaxResult detail(@PathVariable("storeId") Long storeId)
    {
        return AjaxResult.success(storeService.getById(storeId));
    }

    /** 新增门店，新建记录默认启用。 */
    @ApiOperation("新增门店")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody StoreCreateDTO dto)
    {
        storeService.create(dto);
        return success();
    }

    /** 修改门店基础信息；状态通过独立接口修改。 */
    @ApiOperation("修改门店")
    @PutMapping("/{storeId}")
    public AjaxResult update(@PathVariable("storeId") Long storeId, @Valid @RequestBody StoreUpdateDTO dto)
    {
        StoreDetailVO detail = storeService.update(storeId, dto);
        return AjaxResult.success(detail);
    }

    /** 设置门店状态。 */
    @ApiOperation("设置门店状态")
    @PutMapping("/{storeId}/status")
    public AjaxResult updateStatus(@PathVariable("storeId") Long storeId,
            @RequestParam("status") @Min(value = 0, message = "门店状态取值为 0 或 1")
            @Max(value = 1, message = "门店状态取值为 0 或 1") Integer status)
    {
        storeService.updateStatus(storeId, status);
        return success();
    }
}
