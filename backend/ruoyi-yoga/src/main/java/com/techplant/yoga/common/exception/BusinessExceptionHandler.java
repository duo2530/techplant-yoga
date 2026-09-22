package com.techplant.yoga.common.exception;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.techplant.yoga.common.log.BusinessLog;
import com.techplant.yoga.common.response.ApiResponse;

/**
 * 业务模块全局异常处理（详细设计 §3.1.1、§3.2.2：把异常统一转成 {@link ApiResponse}）。
 *
 * <p><b>作用域：</b>{@code basePackages = "com.techplant.yoga"} —— 只处理业务模块自己的 controller。
 * RuoYi 的 {@code sys_*} 接口仍由框架自带的异常处理器处理（它返回的是 {@code AjaxResult}，
 * 前端管理端的既有约定不能被改掉）。优先级取最高，保证业务 controller 上的异常先落到这里。</p>
 *
 * <p><b>状态码：</b>失败时 HTTP 状态码与业务码同值（§2.4 的建议方案 A3）。</p>
 *
 * <p><b>类名说明：</b>详细设计里这个类叫 {@code GlobalExceptionHandler}。RuoYi 框架自带一个
 * {@code com.ruoyi.framework.web.exception.GlobalExceptionHandler}，两者的默认 Bean 名都是
 * {@code globalExceptionHandler}，同时被扫描到会直接抛 {@code ConflictingBeanDefinitionException}
 * 导致应用起不来，因此本模块改名为 {@code BusinessExceptionHandler}（行为与设计一致）。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.techplant.yoga")
public class BusinessExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    /** 参数校验失败的业务码 */
    private static final int BAD_REQUEST = 400;

    /** 系统异常的业务码 */
    private static final int INTERNAL_ERROR = 500;

    /**
     * 业务异常：状态码与明细都来自异常本身（404 课程不存在、409 停用被引用）。
     * <p>对应分支的 WARN 日志已由 service 按 §6.1.2 / §6.1.3 打出，这里只负责组装响应。</p>
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e)
    {
        return ResponseEntity.status(e.getCode())
                .body(ApiResponse.<Object>fail(e.getCode(), e.getMessage(), e.getData()));
    }

    /**
     * 请求体参数校验失败（@Valid）：§6.1.1 埋点（2），WARN 记录失败字段与原始取值
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e, HttpServletRequest request)
    {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message;
        String detail;
        if (fieldError == null)
        {
            message = "参数校验失败";
            detail = message;
        }
        else
        {
            message = "参数校验失败：" + fieldError.getField() + " " + fieldError.getDefaultMessage();
            detail = String.format("field=%s value=%s msg=%s", fieldError.getField(), fieldError.getRejectedValue(),
                    fieldError.getDefaultMessage());
        }
        BusinessLog.warn("VALIDATE", request.getRequestURI(), detail);
        return badRequest(message);
    }

    /**
     * 方法参数校验失败（@Validated + @RequestParam/@PathVariable 约束）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException e,
            HttpServletRequest request)
    {
        StringBuilder detail = new StringBuilder();
        for (ConstraintViolation<?> violation : e.getConstraintViolations())
        {
            if (detail.length() > 0)
            {
                detail.append(", ");
            }
            detail.append(violation.getPropertyPath()).append(" ").append(violation.getMessage());
        }
        String message = "参数校验失败：" + detail;
        BusinessLog.warn("VALIDATE", request.getRequestURI(), detail.toString());
        return badRequest(message);
    }

    /**
     * 路径/查询参数类型不匹配（例如课程编号传了 abc）→ 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
            HttpServletRequest request)
    {
        String detail = String.format("field=%s value=%s", e.getName(), e.getValue());
        BusinessLog.warn("VALIDATE", request.getRequestURI(), detail);
        return badRequest("参数校验失败：" + e.getName() + " 取值不合法");
    }

    /**
     * 请求体不可读（JSON 格式错误等）→ 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageNotReadable(HttpMessageNotReadableException e,
            HttpServletRequest request)
    {
        BusinessLog.warn("VALIDATE", request.getRequestURI(), "请求体不可解析");
        return badRequest("参数校验失败：请求体格式不正确");
    }

    /**
     * 缺少必填查询参数 → 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException e,
            HttpServletRequest request)
    {
        BusinessLog.warn("VALIDATE", request.getRequestURI(), "missing=" + e.getParameterName());
        return badRequest("参数校验失败：缺少必填参数 " + e.getParameterName());
    }

    /**
     * 非预期异常：§6.1.1 埋点（4），ERROR 记录异常摘要 + 链路标识，不向前端暴露堆栈
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e, HttpServletRequest request)
    {
        BusinessLog.error("UNKNOWN", request.getRequestURI(), "非预期异常：" + e.getClass().getSimpleName(), e);
        log.error("业务接口出现非预期异常, uri={}", request.getRequestURI(), e);
        return ResponseEntity.status(INTERNAL_ERROR)
                .body(ApiResponse.<Object>fail(INTERNAL_ERROR, "系统异常，请稍后重试"));
    }

    private ResponseEntity<ApiResponse<Object>> badRequest(String message)
    {
        return ResponseEntity.status(BAD_REQUEST).body(ApiResponse.<Object>fail(BAD_REQUEST, message));
    }
}

