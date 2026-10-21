package com.techplant.yoga.common.web;

import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 新增接口的参数错误口径。
 *
 * <p>这些接口按变更契约返回业务码 400，而全局异常处理器把参数校验失败、缺失与类型转换失败
 * 统一映射为 500；本基类只覆盖继承它的新增接口，不改动全局处理器与既有接口行为。</p>
 */
public abstract class AbstractParamErrorController extends BaseController {
    /** 参数错误业务码 */
    private static final int PARAM_ERROR = 400;

    /** 路径或查询参数类型转换失败 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return AjaxResult.error(PARAM_ERROR, "参数[" + e.getName() + "]格式非法");
    }

    /** 必填查询参数缺失 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public AjaxResult handleMissingParameter(MissingServletRequestParameterException e) {
        return AjaxResult.error(PARAM_ERROR, "参数[" + e.getParameterName() + "]不能为空");
    }

    /** 查询参数校验失败（GET 请求的模型属性绑定） */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e) {
        return AjaxResult.error(PARAM_ERROR, messageOf(e.getBindingResult()));
    }

    /** 请求体校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return AjaxResult.error(PARAM_ERROR, messageOf(e.getBindingResult()));
    }

    private String messageOf(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError == null) {
            return "参数格式非法";
        }
        // 类型转换失败时默认消息是框架英文原文，改与路径参数一致的中文提示
        if (isTypeMismatch(fieldError)) {
            return "参数[" + fieldError.getField() + "]格式非法";
        }
        String message = fieldError.getDefaultMessage();
        return message == null ? "参数格式非法" : message;
    }

    private boolean isTypeMismatch(FieldError fieldError) {
        if ("typeMismatch".equals(fieldError.getCode())) {
            return true;
        }
        String[] codes = fieldError.getCodes();
        if (codes != null) {
            for (String code : codes) {
                if (code != null && code.startsWith("typeMismatch")) {
                    return true;
                }
            }
        }
        return false;
    }
}
