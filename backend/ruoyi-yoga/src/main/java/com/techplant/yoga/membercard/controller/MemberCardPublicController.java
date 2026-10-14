package com.techplant.yoga.membercard.controller;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.member.security.MemberContextUtils;
import com.techplant.yoga.membercard.query.MemberCardPublicQuery;
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.vo.MemberCardPublicItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端「我的会员卡」接口。 */
@Api(tags = "用户端-我的会员卡")
@Validated
@RestController
@RequestMapping("/api/member-cards")
public class MemberCardPublicController extends BaseController
{
    private final MemberCardService memberCardService;

    public MemberCardPublicController(MemberCardService memberCardService)
    {
        this.memberCardService = memberCardService;
    }

    /**
     * 查询我的会员卡：会员 ID 只取登录态，客户端传入值不作为访问依据
     *
     * <p>未登录或登录态失效由鉴权链与 {@link MemberContextUtils} 统一返回业务码 401。</p>
     */
    @ApiOperation("查询我的会员卡")
    @GetMapping
    public TableDataInfo list(@Valid MemberCardPublicQuery query)
    {
        PageResult<MemberCardPublicItemVO> page = memberCardService.pageForMember(
                MemberContextUtils.requireMemberId(), query);
        TableDataInfo dataTable = new TableDataInfo();
        dataTable.setCode(HttpStatus.SUCCESS);
        dataTable.setMsg("查询成功");
        // 无卡或页码超出范围都返回空 rows，total 为本人会员卡总数
        dataTable.setRows(page.getList());
        dataTable.setTotal(page.getTotal() == null ? 0L : page.getTotal());
        return dataTable;
    }
}
