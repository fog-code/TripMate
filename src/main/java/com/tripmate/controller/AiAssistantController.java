package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 旅游助手接口。
 *
 * POST /api/ai/chat      → 普通对话
 * POST /api/ai/chat/vip  → 个性化对话（结合用户行为数据）
 * GET  /api/ai/chat/stream → 流式对话（SSE）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    /**
     * 普通对话。
     * Body: { "message": "推荐几个适合春天旅游的地方" }
     */
    @PostMapping("/chat")
    public Result<?> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error(400, "消息不能为空");
        }
        String reply = aiAssistantService.chat(message);
        return Result.success(Map.of("reply", reply));
    }

    /**
     * 个性化对话（结合数据库用户行为 + 热门景点）。
     */
    @PostMapping("/chat/vip")
    public Result<?> chatVip(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error(400, "消息不能为空");
        }
        String reply = aiAssistantService.chatWithContext(message);
        return Result.success(Map.of("reply", reply));
    }

    /**
     * 流式对话（SSE），前端可使用 EventSource 或 fetch 读取。
     * 请求参数：?message=推荐北京三日游
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(required = false, defaultValue = "") String message) {
        // 1. 校验（此时 message 不会是 null，只需检查 blank）
        if (message.isBlank()) {
            return Flux.just(
                    "data: 消息不能为空\n\n",
                    "data: [DONE]\n\n"
            );
        }

        // 2. 调用服务 + 格式化 SSE + 结束标志 + 异常降级
        return aiAssistantService.chatStream(message)
                .map(chunk -> "data: " + chunk + "\n\n")
                .concatWith(Flux.just("data: [DONE]\n\n"))
                .onErrorResume(e -> {
                    log.error("AI流式输出异常, message={}", message, e);
                    return Flux.just(
                            "data: 哎呀，小助手暂时走神了，请稍后再试~\n\n",
                            "data: [DONE]\n\n"
                    );
                });
    }
}
