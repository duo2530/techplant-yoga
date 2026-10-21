package com.techplant.yoga.coach.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.common.web.AbstractParamErrorController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 管理端教练等级接口。
 *
 * <p>独立成 controller 是为了让 Swagger 直接呈现「coachId 路径参数 + level 为必填整数」；
 * 参数错误沿用 {@link AbstractParamErrorController} 的 400 口径，既有教练管理接口的错误码不受影响。</p>
 */
@Api(tags = "管理端-教练管理")
@RestController
@RequestMapping("/admin/coaches")
@RequiredArgsConstructor
public class CoachLevelController extends AbstractParamErrorController {
    private final CoachService coachService;

    /** 设置／取消金牌：level=1 普通、2 金牌；重复设置同一等级幂等成功 */
    @ApiOperation("设置教练等级")
    @PreAuthorize("@ss.hasPermi('yoga:coach:level')")
    @PutMapping("/{coachId}/level")
    public AjaxResult update(@PathVariable("coachId") Long coachId, @RequestParam("level") Integer level) {
        coachService.updateLevel(coachId, level);
        return success();
    }
}
