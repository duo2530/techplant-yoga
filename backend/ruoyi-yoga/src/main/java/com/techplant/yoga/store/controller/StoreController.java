package com.techplant.yoga.store.controller;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

/**
 * 管理端门店管理接口（门店管理详细设计 §2.2）。
 *
 * <table>
 * <caption>接口清单</caption>
 * <tr><td>GET /admin/stores</td><td>查询门店列表</td></tr>
 * <tr><td>GET /admin/stores/{storeId}</td><td>查询门店详情</td></tr>
 * <tr><td>POST /admin/stores</td><td>新增门店（不返回新 ID）</td></tr>
 * <tr><td>PUT /admin/stores/{storeId}</td><td>修改门店（全量编辑，返回最新详情）</td></tr>
 * <tr><td>DELETE /admin/stores/{storeId}</td><td>删除门店（物理，带门禁）</td></tr>
 * </table>
 *
 * <p><b>没有状态接口</b>：一期门店没有状态字段（BR-门店-008）。</p>
 *
 * <p>响应口径与若依既有体系一致：列表 {@link TableDataInfo}{@code {total,rows,code,msg}}，
 * 单条 {@link AjaxResult}{@code {code,msg,data}}；业务失败由 service 抛 {@code ServiceException}，
 * 交给框架唯一的全局异常处理器（404／409／500）。controller 只调 service、做 {@code @Valid}
 * 校验并装配响应，不写业务逻辑（§3.2）。</p>
 */
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

    /** 查询门店列表（§2.2.1）：name 模糊、regionCode 精确、storeType 精确 */
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

    /** 查询门店详情（§2.2.2）：不存在 → 业务码 404「门店不存在或已被删除」 */
    @ApiOperation("查询门店详情")
    @GetMapping("/{storeId}")
    public AjaxResult detail(@PathVariable("storeId") Long storeId)
    {
        StoreDetailVO detail = storeService.getById(storeId);
        return AjaxResult.success(detail);
    }

    /** 新增门店（§2.2.3）：名称重复 → 409；成功只返回 {@code {code:200,msg:操作成功}}，不返回新 ID */
    @ApiOperation("新增门店")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody StoreCreateDTO dto)
    {
        storeService.create(dto);
        return success();
    }

    /** 修改门店（§2.2.4）：全量编辑，名称重复（排除自身）→ 409；成功返回更新后的详情 */
    @ApiOperation("修改门店")
    @PutMapping("/{storeId}")
    public AjaxResult update(@PathVariable("storeId") Long storeId, @Valid @RequestBody StoreUpdateDTO dto)
    {
        StoreDetailVO detail = storeService.update(storeId, dto);
        return AjaxResult.success(detail);
    }

    /** 删除门店（§2.2.5）：物理删除；名下有教室或未结束排课 → 409；统计失败 fail-closed → 409 */
    @ApiOperation("删除门店")
    @DeleteMapping("/{storeId}")
    public AjaxResult remove(@PathVariable("storeId") Long storeId)
    {
        storeService.delete(storeId);
        return success();
    }
}
