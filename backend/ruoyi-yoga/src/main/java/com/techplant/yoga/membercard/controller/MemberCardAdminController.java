package com.techplant.yoga.membercard.controller;

import lombok.RequiredArgsConstructor;

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
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.query.MemberCardQuery;
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端会员卡管理接口。 */
@Api(tags = "管理端-会员卡管理")
@Validated
@RestController
@RequestMapping("/admin/member-cards")
@RequiredArgsConstructor
public class MemberCardAdminController extends BaseController {
    private final MemberCardService memberCardService;

    /** 查询会员卡列表：memberId 精确、cardNo 模糊、cardType 精确、storeId 按适用门店精确，最新发卡在前 */
    @ApiOperation("查询会员卡列表")
    @GetMapping
    public TableDataInfo list(@Valid MemberCardQuery query) {
        PageResult<MemberCardListItemVO> page = memberCardService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 查询会员卡详情：不存在 → 业务码 404「会员卡不存在或已被删除」 */
    @ApiOperation("查询会员卡详情")
    @GetMapping("/{cardId}")
    public AjaxResult detail(@PathVariable("cardId") Long cardId) {
        MemberCardDetailVO detail = memberCardService.getById(cardId);
        return AjaxResult.success("查询成功", detail);
    }

    /** 发卡（创建待开卡）：卡号由服务端生成；成功只返回 {@code {code:200,msg:操作成功}}，不返回新 ID */
    @ApiOperation("发卡（创建待开卡）")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody MemberCardCreateDTO dto) {
        memberCardService.create(dto);
        return success();
    }

    /** 正式开卡：无请求体，有效期按当前自然日计算；成功返回开卡后的详情 */
    @ApiOperation("正式开卡")
    @PutMapping("/{cardId}/activate")
    public AjaxResult activate(@PathVariable("cardId") Long cardId) {
        MemberCardDetailVO detail = memberCardService.activate(cardId);
        return AjaxResult.success(detail);
    }

    /** 停卡：原因必填；成功返回停卡后的详情 */
    @ApiOperation("停卡")
    @PutMapping("/{cardId}/stop")
    public AjaxResult stop(@PathVariable("cardId") Long cardId, @Valid @RequestBody MemberCardStopDTO dto) {
        MemberCardDetailVO detail = memberCardService.stop(cardId, dto);
        return AjaxResult.success(detail);
    }

    /** 删除会员卡：物理删除 */
    @ApiOperation("删除会员卡")
    @DeleteMapping("/{cardId}")
    public AjaxResult remove(@PathVariable("cardId") Long cardId) {
        memberCardService.delete(cardId);
        return success();
    }
}
