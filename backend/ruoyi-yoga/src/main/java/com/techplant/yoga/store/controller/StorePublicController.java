package com.techplant.yoga.store.controller;

import lombok.RequiredArgsConstructor;

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

/** 用户端门店列表接口。 */
@Api(tags = "用户端-门店")
@Anonymous
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StorePublicController extends BaseController {
    private final StoreService storeService;

    /** 用户端查询门店列表（分页，免登录） */
    @ApiOperation("用户端查询门店列表")
    @GetMapping
    public TableDataInfo list(@Valid StorePublicQuery query) {
        PageResult<StorePublicItemVO> page = storeService.publicPage(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
