package com.techplant.yoga.store.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 游客门店查询接口（门店管理详细设计 §2.3）。 */
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

    /** 游客可查询启用门店列表，无需登录。 */
    @ApiOperation("游客查询门店列表")
    @GetMapping
    public AjaxResult list()
    {
        List<StorePublicItemVO> stores = storeService.publicList();
        return AjaxResult.success(stores);
    }
}
