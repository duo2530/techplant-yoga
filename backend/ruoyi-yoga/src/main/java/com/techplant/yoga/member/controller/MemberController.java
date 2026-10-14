package com.techplant.yoga.member.controller;

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
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.dto.MemberUpdateDTO;
import com.techplant.yoga.member.query.MemberQuery;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.vo.MemberDetailVO;
import com.techplant.yoga.member.vo.MemberListItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 管理端会员管理接口。 */
@Api(tags = "管理端-会员管理")
@Validated
@RestController
@RequestMapping("/admin/members")
public class MemberController extends BaseController
{
    private final MemberService memberService;

    public MemberController(MemberService memberService)
    {
        this.memberService = memberService;
    }

    /** 查询会员列表：手机号后四位精确、显示名模糊、等级精确，按会员 ID 降序 */
    @ApiOperation("查询会员列表")
    @GetMapping
    public TableDataInfo list(@Valid MemberQuery query)
    {
        PageResult<MemberListItemVO> page = memberService.page(query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }

    /** 查询会员详情：不存在 → 业务码 404「会员不存在或已被删除」 */
    @ApiOperation("查询会员详情")
    @GetMapping("/{memberId}")
    public AjaxResult detail(@PathVariable("memberId") Long memberId)
    {
        MemberDetailVO detail = memberService.getById(memberId);
        return AjaxResult.success("查询成功", detail);
    }

    /** 新增会员：只建档不建账号，显示名由服务端生成；成功不返回新 ID */
    @ApiOperation("新增会员")
    @PostMapping
    public AjaxResult create(@Valid @RequestBody MemberCreateDTO dto)
    {
        memberService.create(dto);
        return success();
    }

    /** 修改会员：只维护性别与等级，省略字段保留原值；成功返回更新后的详情 */
    @ApiOperation("修改会员")
    @PutMapping("/{memberId}")
    public AjaxResult update(@PathVariable("memberId") Long memberId, @Valid @RequestBody MemberUpdateDTO dto)
    {
        MemberDetailVO detail = memberService.update(memberId, dto);
        return AjaxResult.success(detail);
    }

    /** 删除会员：物理删除，登录凭证随行删除，既有登录态因会员不存在而失效 */
    @ApiOperation("删除会员")
    @DeleteMapping("/{memberId}")
    public AjaxResult remove(@PathVariable("memberId") Long memberId)
    {
        memberService.delete(memberId);
        return success();
    }
}
