package com.tripmate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.common.UserContext;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.UserBehavior;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.UserBehaviorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 旅游助手服务。
 * - chat/chatWithContext: 普通 HTTP 调用
 * - chatStream: 真正的 SSE 流式调用 DeepSeek
 */
@Slf4j
@Service
public class AiAssistantService {

    private final RestClient restClient;
    private final RestClient streamClient;
    private final ObjectMapper objectMapper;
    private final AttractionMapper attractionMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    private static final List<String> ENGAGEMENT_ACTIONS = List.of("VIEW", "LIKE", "BOOKMARK");

    public AiAssistantService(@Value("${spring.ai.openai.api-key}") String apiKey,
                              AttractionMapper attractionMapper,
                              UserBehaviorMapper userBehaviorMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.streamClient = RestClient.builder()
                .baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
                .build();
        this.objectMapper = new ObjectMapper();
        this.attractionMapper = attractionMapper;
        this.userBehaviorMapper = userBehaviorMapper;
    }

    // 已知的城市和分类，用于从用户消息中提取意图
    private static final List<String> KNOWN_CITIES = List.of(
            "北京", "重庆", "上海", "成都", "杭州", "西安", "广州", "深圳",
            "南京", "苏州", "昆明", "厦门", "青岛", "武汉", "长沙", "三亚",
            "桂林", "哈尔滨", "大理", "丽江");
    private static final List<String> KNOWN_CATEGORIES = List.of(
            "历史古迹", "自然风光", "博物馆", "主题公园", "城市景观",
            "古镇街区", "宗教场所", "文化创意", "购物中心", "美食街区");

    private static final int MAX_ATTRACTIONS_IN_PROMPT = 30;

    // ═══════════════════════════════════════════════════
    // 普通对话
    // ═══════════════════════════════════════════════════

    public String chat(String userMessage) {
        String systemPrompt = buildSystemPrompt() + buildDatabaseAttractions(userMessage);
        return callDeepSeek(systemPrompt, userMessage);
    }

    public String chatWithContext(String userMessage) {
        String systemPrompt = buildSystemPrompt()
                + buildPersonalizedContext()
                + buildDatabaseAttractions(userMessage);
        return callDeepSeek(systemPrompt, userMessage);
    }

    private String callDeepSeek(String systemPrompt, String userMessage) {
        try {
            log.info("DeepSeek request: {}", userMessage.substring(0, Math.min(50, userMessage.length())));

            List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);
            Map<String, Object> body = buildBody(messages, false);
            String json = objectMapper.writeValueAsString(body);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(json)
                    .retrieve()
                    .body(String.class);

            String content = extractContent(response);
            log.info("DeepSeek response length: {}", content != null ? content.length() : 0);
            return content != null ? content : "AI 返回了空内容。";
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage(), e);
            return "AI 服务出错：" + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════
    // 流式对话
    // ═══════════════════════════════════════════════════

    /**
     * 流式对话：设置 stream=true，解析 SSE 事件，逐个发射 delta。
     *
     */
    public Flux<String> chatStream(String userMessage) {
        String systemPrompt = buildSystemPrompt()
                + buildPersonalizedContext()
                + buildDatabaseAttractions(userMessage);

        return Flux.create(sink -> {
            try {
                List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);
                Map<String, Object> body = buildBody(messages, true);
                String json = objectMapper.writeValueAsString(body);

                log.info("DeepSeek stream request: {}", userMessage.substring(0, Math.min(50, userMessage.length())));

                streamClient.post()
                        .uri("/chat/completions")
                        .body(json)
                        .exchange((req, resp) -> {
                            // ── 关键修复：检查 HTTP 状态码 ──
                            HttpStatus statusCode = (HttpStatus) resp.getStatusCode();
                            if (!statusCode.is2xxSuccessful()) {
                                String errorBody;
                                try (BufferedReader errReader = new BufferedReader(
                                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                                    errorBody = errReader.lines().collect(Collectors.joining("\n"));
                                } catch (Exception e) {
                                    errorBody = "无法读取错误响应";
                                }
                                log.error("DeepSeek stream HTTP error {}: {}", statusCode.value(), errorBody);
                                sink.next("AI 服务返回错误 (HTTP " + statusCode.value() + ")，请稍后再试");
                                sink.complete();
                                return null;
                            }

                            // ── 正常流式响应：逐行解析 SSE ──
                            boolean hasContent = false;
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6).trim();
                                        if ("[DONE]".equals(data)) {
                                            break;
                                        }
                                        try {
                                            JsonNode node = objectMapper.readTree(data);
                                            JsonNode choices = node.get("choices");
                                            if (choices != null && choices.size() > 0) {
                                                JsonNode delta = choices.get(0).get("delta");
                                                if (delta != null) {
                                                    JsonNode contentNode = delta.get("content");
                                                    if (contentNode != null && !contentNode.isNull()) {
                                                        String chunk = contentNode.asText();
                                                        if (!chunk.isEmpty()) {
                                                            hasContent = true;
                                                            sink.next(chunk);
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception parseErr) {
                                            // 跳过无法解析的行（可能是 DeepSeek 的非标准 SSE 数据）
                                            log.debug("Skip unparseable SSE line: {}", data);
                                        }
                                    }
                                }
                            }

                            if (!hasContent) {
                                log.warn("DeepSeek stream returned zero content");
                                sink.next("AI 返回了空内容，请重新提问。");
                            }
                            sink.complete();
                            return null;
                        });

            } catch (Exception e) {
                log.error("DeepSeek stream failed: {}", e.getMessage(), e);
                sink.next("AI 服务出错：" + e.getMessage());
                sink.complete();
            }
        });
    }

    // ═══════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════

    private List<Map<String, String>> buildMessages(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private Map<String, Object> buildBody(List<Map<String, String>> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 4096);
        body.put("stream", stream);
        return body;
    }

    private String extractContent(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    return content != null ? content.asText() : null;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse DeepSeek response");
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    // 上下文构建
    // ═══════════════════════════════════════════════════

    private String buildSystemPrompt() {
        return """
            你是「旅享」旅游推荐平台的 AI 智能助手，名叫"小旅"。
            你是一位热情、专业的旅游规划专家。

            【重要约束】你必须严格遵守以下规则：
            1. 你只能推荐下方「平台景点库」中列出的景点，绝不能推荐不在列表中的景点。
            2. 如果用户问的景点不在库中，诚实告知并推荐库里同城市/同类型的替代景点。
            3. 用中文回复，语气亲切友好，适当使用 emoji。
            4. 回答简洁有条理，必要时使用序号分段。
            5. 每次回复控制在 500 字以内。

            """;
    }

    /**
     * 智能构建景点上下文：从用户消息中提取城市/分类/关键词，
     * 只送入匹配的景点，上限 {@value MAX_ATTRACTIONS_IN_PROMPT} 个。
     * <p>无匹配时退回热门 Top N。</p>
     */
    private String buildDatabaseAttractions(String userMessage) {
        // ── 1. 提取用户意图 ──
        String targetCity = null;
        String targetCategory = null;

        String msg = userMessage != null ? userMessage : "";

        for (String city : KNOWN_CITIES) {
            if (msg.contains(city)) { targetCity = city; break; }
        }
        for (String cat : KNOWN_CATEGORIES) {
            if (msg.contains(cat)) { targetCategory = cat; break; }
        }

        // ── 2. 查询景点 ──
        LambdaQueryWrapper<Attraction> wrapper = new LambdaQueryWrapper<Attraction>()
                .eq(Attraction::getStatus, 1);

        if (targetCity != null) {
            wrapper.eq(Attraction::getCity, targetCity);
        }
        if (targetCategory != null) {
            wrapper.eq(Attraction::getCategory, targetCategory);
        }
        if (targetCity != null || targetCategory != null) {
            wrapper.orderByDesc(Attraction::getViewCount);
        } else {
            // 无明确城市/分类：取最热门景点
            wrapper.orderByDesc(Attraction::getViewCount);
        }
        wrapper.last("LIMIT " + MAX_ATTRACTIONS_IN_PROMPT);

        List<Attraction> filtered = attractionMapper.selectList(wrapper);

        // ── 3. 如果筛选后为空，退回热门 Top N ──
        if (filtered.isEmpty() && (targetCity != null || targetCategory != null)) {
            wrapper = new LambdaQueryWrapper<Attraction>()
                    .eq(Attraction::getStatus, 1)
                    .orderByDesc(Attraction::getViewCount)
                    .last("LIMIT " + MAX_ATTRACTIONS_IN_PROMPT);
            filtered = attractionMapper.selectList(wrapper);
        }

        // ── 4. 构建文本 ──
        StringBuilder sb = new StringBuilder();
        sb.append("【平台景点库 — 你只能推荐这些景点！】\n");
        if (filtered.isEmpty()) {
            sb.append("暂无景点数据。\n");
            return sb.toString();
        }

        sb.append(String.format("（共 %d 个景点，已根据你的问题智能筛选）\n", filtered.size()));

        String currentCity = "";
        for (Attraction a : filtered) {
            if (!a.getCity().equals(currentCity)) {
                currentCity = a.getCity();
                sb.append("\n").append(currentCity).append("：\n");
            }
            sb.append("  - ").append(a.getName())
              .append(" | ").append(a.getCategory())
              .append(" | 评分").append(a.getRating() != null ? a.getRating() : 4)
              .append("\n");
        }
        return sb.toString();
    }

    private String buildPersonalizedContext() {
        StringBuilder ctx = new StringBuilder();
        Long userId = null;
        try { userId = UserContext.getUserId(); } catch (Exception ignored) {}

        if (userId != null) {
            LambdaQueryWrapper<UserBehavior> bw = new LambdaQueryWrapper<>();
            bw.eq(UserBehavior::getUserId, userId)
              .in(UserBehavior::getActionType, ENGAGEMENT_ACTIONS)
              .orderByDesc(UserBehavior::getCreatedAt).last("LIMIT 20");
            List<UserBehavior> behaviors = userBehaviorMapper.selectList(bw);

            if (!behaviors.isEmpty()) {
                List<Long> ids = behaviors.stream().map(UserBehavior::getAttractionId).distinct().toList();
                List<Attraction> interacted = attractionMapper.selectBatchIds(ids);
                Map<Long, Attraction> attrMap = interacted.stream()
                        .collect(Collectors.toMap(Attraction::getId, a -> a));

                ctx.append("【用户偏好】\n");
                ctx.append("- 近期互动景点: ").append(behaviors.stream()
                        .map(b -> attrMap.getOrDefault(b.getAttractionId(), null))
                        .filter(Objects::nonNull).map(a -> a.getName() + "(" + a.getCity() + ")")
                        .distinct().limit(8).collect(Collectors.joining("、"))).append("\n");
            }
        }
        if (ctx.isEmpty()) {
            ctx.append("暂无用户的个性化数据。\n");
        }
        return ctx.toString();
    }
}
