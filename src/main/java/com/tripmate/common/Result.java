package com.tripmate.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一 API 响应包装类。
 *
 * <p>所有控制器的返回值都通过此类包装，确保前端收到一致的 JSON 结构：
 * <pre>{@code
 * { "code": 0, "message": "success", "data": {...} }
 * }</pre>
 * code=0 表示成功，非 0 表示错误（400=业务异常，500=服务器错误）。</p>
 *
 * @param <T> data 的具体类型
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码：0=成功，其他=错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据，null 时不序列化 */
    private T data;

    /** 快捷创建成功响应（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /** 快捷创建成功响应（无数据，如删除操作） */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    /** 快捷创建错误响应 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
