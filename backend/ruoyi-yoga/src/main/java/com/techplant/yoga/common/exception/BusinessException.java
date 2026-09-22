package com.techplant.yoga.common.exception;

/**
 * 业务异常基类（详细设计 §3.1.1、§3.2.2）。
 *
 * <p>携带 {@code code} / {@code message} / {@code data} 三部分：业务码与 HTTP 状态码同值（§2.4），
 * {@code data} 用于承载失败明细（例如停用被引用时的阻塞明细），由
 * {@link GlobalExceptionHandler} 组装成 {@code ApiResponse}。</p>
 */
public class BusinessException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** 业务码，同时也是响应的 HTTP 状态码 */
    private final int code;

    /** 失败明细，可为 null */
    private final transient Object data;

    public BusinessException(int code, String message)
    {
        this(code, message, null);
    }

    public BusinessException(int code, String message, Object data)
    {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode()
    {
        return code;
    }

    public Object getData()
    {
        return data;
    }
}
