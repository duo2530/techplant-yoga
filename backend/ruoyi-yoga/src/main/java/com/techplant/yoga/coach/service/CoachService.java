package com.techplant.yoga.coach.service;

import java.util.Collection;
import java.util.List;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;

/**
 * 教练业务服务（教练管理详细设计 §3.2）。
 *
 * <p>本接口同时承担两类对外职责（全项目统一不另立 QueryService，详细设计总览 §6）：</p>
 * <ol>
 *   <li><b>管理端</b>：列表／详情／新增／修改／删除（物理，带排课删除门禁）；</li>
 *   <li><b>跨模块</b>：向排课模块提供「批量取姓名头像简介」{@link #summaries(Collection)} 与
 *       「取单个教练摘要」{@link #getSummary(Long)}。</li>
 * </ol>
 *
 * <p>教练<b>没有状态</b>（无启用／停用接口），名称<b>允许重名</b>（不做查重）。</p>
 */
public interface CoachService
{
    /** 管理端列表分页（按 id DESC；<b>不返回</b> intro 与 gallery） */
    PageResult<CoachListItemVO> page(CoachQuery query);

    /** 查询教练详情；不存在 → 404「教练不存在或已被删除」 */
    CoachDetailVO getById(Long coachId);

    /** 新增教练：姓名重复<b>放行</b>；相册 > 5 → 409「相册最多 5 张」 */
    void create(CoachCreateDTO dto);

    /** 修改教练：全量编辑；不存在 → 404；相册 > 5 → 409；<b>不做引用检查</b>；成功返回最新详情 */
    CoachDetailVO update(Long coachId, CoachUpdateDTO dto);

    /**
     * 删除教练（<b>物理删除</b>）：
     * ① 不存在 → 404；② 有「未结束且待上架/已上架」的排课引用 → 409（带数量）；
     * ③ 统计调用失败 → 409「引用检查未完成，已拒绝本次删除」（fail-closed，不降级放行）。
     */
    void delete(Long coachId);

    /**
     * 按 ID 集合批量取教练摘要（排课模块补齐 coachName / coachAvatarUrl 用）。
     *
     * @param ids 教练ID集合，允许为空（返回空列表）
     * @return 命中的教练摘要；已被物理删除的 ID 不会出现在结果里
     */
    List<CoachSummaryVO> summaries(Collection<Long> ids);

    /**
     * 取单个教练摘要（排课模块校验教练存在用）。
     *
     * @param coachId 教练ID
     * @return 教练摘要
     * @throws com.ruoyi.common.exception.ServiceException 教练不存在时抛 404「教练不存在或已被删除」
     */
    CoachSummaryVO getSummary(Long coachId);
}
