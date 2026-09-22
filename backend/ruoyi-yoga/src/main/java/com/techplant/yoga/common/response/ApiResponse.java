package com.techplant.yoga.common.response;

/**
 * 统一响应结构（详细设计 §2.3.1）。
 *
 * <p>业务码与 HTTP 状态码同值，成功是唯一例外：HTTP 200 + {@code code = 0}（§2.4）。</p>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T>
{
    /** 成功业务码 */
    public static final int SUCCESS_CODE = 0;

    /** 成功提示信息 */
    public static final String SUCCESS_MESSAGE = "success";

    /** 业务码，0 表示成功；失败时与 HTTP 状态码同值 */
    private Integer code;

    /** 提示信息，成功固定 success */
    private String message;

    /** 业务数据；无返回值时为 null */
    private T data;

    public ApiResponse()
    {
    }

    public ApiResponse(Integer code, String message, T data)
    {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data)
    {
        return new ApiResponse<T>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 成功响应（无数据，data 为 null）
     */
    public static <T> ApiResponse<T> success()
    {
        return new ApiResponse<T>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 失败响应（无附加数据）
     */
    public static <T> ApiResponse<T> fail(Integer code, String message)
    {
        return new ApiResponse<T>(code, message, null);
    }

    /**
     * 失败响应（带明细数据，例如停用被引用时的阻塞明细）
     */
    public static <T> ApiResponse<T> fail(Integer code, String message, T data)
    {
        return new ApiResponse<T>(code, message, data);
    }

    public Integer getCode()
    {
        return code;
    }

    public void setCode(Integer code)
    {
        this.code = code;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public T getData()
    {
        return data;
    }

    public void setData(T data)
    {
        this.data = data;
    }
}
