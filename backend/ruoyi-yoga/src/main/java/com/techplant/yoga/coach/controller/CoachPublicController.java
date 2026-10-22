package com.techplant.yoga.coach.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.techplant.yoga.coach.service.CoachService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 用户端教练公开接口（免登录只读）。 */
@Api(tags = "用户端-教练")
@Anonymous
@RestController
@RequestMapping("/api/coaches")
@RequiredArgsConstructor
public class CoachPublicController extends BaseController {
    private final CoachService coachService;

    /** 全部金牌教练：一次返回完整列表，按 id DESC，不截断 */
    @ApiOperation("查询全部金牌教练")
    @GetMapping("/featured")
    public AjaxResult featured() {
        return AjaxResult.success(coachService.featured());
    }

    /** 教练公开详情：不要求仍为金牌；不存在或已删除 → 404，ID 格式非法 → 400 */
    @ApiOperation("查询教练公开详情")
    @GetMapping("/{coachId}")
    public AjaxResult detail(@PathVariable("coachId") Long coachId) {
        return AjaxResult.success(coachService.getPublicDetail(coachId));
    }
}
