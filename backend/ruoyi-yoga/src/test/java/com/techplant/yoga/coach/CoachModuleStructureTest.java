package com.techplant.yoga.coach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import com.techplant.yoga.coach.controller.CoachController;
import com.techplant.yoga.coach.service.CoachService;

/**
 * 教练模块「旧设计残留」的结构守卫（教练管理详细设计 §2.1 第 5 条、BR-用户端-010）。
 *
 * <p>一期用户端<b>不提供</b>教练列表与详情页，因此 {@code /api/coaches} 不存在；教练也<b>没有状态接口</b>。
 * 这几条用「类必须不存在 / 方法必须不存在」来锁死，防止后续有人照旧设计把类加回来。</p>
 */
@DisplayName("教练模块结构守卫（旧类已删除 + 服务契约冻结）")
class CoachModuleStructureTest
{
    /** 旧设计里必须已经删除的类（删掉后 {@code Class.forName} 应抛 ClassNotFoundException） */
    private static final Set<String> REMOVED_CLASSES = new LinkedHashSet<String>(Arrays.asList(
            "com.techplant.yoga.coach.controller.CoachPublicController",
            "com.techplant.yoga.coach.dto.CoachStatusDTO",
            "com.techplant.yoga.coach.vo.CoachCardVO",
            "com.techplant.yoga.coach.vo.CoachIdVO"));

    /** CoachService 的冻结方法集合（排课模块依赖 summaries / getSummary，其它旧方法不许回来） */
    private static final Set<String> EXPECTED_SERVICE_METHODS = new LinkedHashSet<String>(Arrays.asList(
            "page", "getById", "create", "update", "delete", "summaries", "getSummary"));

    /** 旧设计的服务方法（用户端列表/详情、金牌教练、状态变更）一个都不许回来 */
    private static final Set<String> FORBIDDEN_SERVICE_METHODS = new LinkedHashSet<String>(Arrays.asList(
            "publicPage", "featured", "updateStatus", "publicDetail"));

    @Test
    @DisplayName("用户端与状态相关的旧类必须已经删除（BR-用户端-010 + 教练无状态）")
    void removedClasses_shouldNotExist()
    {
        for (String className : REMOVED_CLASSES)
        {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
                    "按新设计这个类必须已经删除：" + className);
        }
    }

    @Test
    @DisplayName("CoachService 只保留 7 个方法：5 个管理端 + 2 个跨模块摘要")
    void serviceInterface_shouldOnlyExposeFrozenMethods()
    {
        Set<String> actual = new LinkedHashSet<String>();
        for (Method method : CoachService.class.getDeclaredMethods())
        {
            actual.add(method.getName());
        }
        assertEquals(EXPECTED_SERVICE_METHODS, actual);
    }

    @Test
    @DisplayName("CoachService 不得再有 updateStatus / publicPage / featured（教练无状态、用户端无教练接口）")
    void serviceInterface_shouldNotExposeLegacyMethods()
    {
        Set<String> actual = new LinkedHashSet<String>();
        for (Method method : CoachService.class.getDeclaredMethods())
        {
            actual.add(method.getName());
        }
        for (String forbidden : FORBIDDEN_SERVICE_METHODS)
        {
            assertFalse(actual.contains(forbidden), "旧方法必须已删除：" + forbidden);
        }
    }

    @Test
    @DisplayName("管理端 controller 只挂在 /admin/coaches（用户端 /api/coaches 一期不存在）")
    void controller_shouldOnlyMapAdminPath()
    {
        RequestMapping mapping = CoachController.class.getAnnotation(RequestMapping.class);

        assertTrue(mapping != null, "CoachController 必须标 @RequestMapping");
        assertEquals(1, mapping.value().length);
        assertEquals("/admin/coaches", mapping.value()[0]);
    }
}
