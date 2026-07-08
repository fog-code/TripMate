package com.tripmate.exception;

import com.tripmate.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一拦截所有控制器抛出的异常并转换为 Result 格式返回。
 *
 * <p>异常映射规则：
 * <ul>
 *   <li>{@code RuntimeException} → HTTP 400（业务异常，消息透传给前端）</li>
 *   <li>{@code MethodArgumentNotValidException} → HTTP 400（参数校验失败，返回第一条错误）</li>
 *   <li>{@code Exception} → HTTP 500（未预期的服务器错误，不暴露内部细节）</li>
 * </ul></p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（如"用户名已存在"、"景点不存在"等），返回 400 + 中文消息 */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /** 请求参数校验失败（@Valid 校验不通过），返回第一条字段错误消息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, msg);
    }

    /** 未预期异常，返回 500 + 通用错误消息，打印完整堆栈供排查 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("未捕获异常", e);
        return Result.error(500, "服务器内部错误");
    }
}
