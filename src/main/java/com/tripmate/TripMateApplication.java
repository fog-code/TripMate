package com.tripmate;

import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TripMate 旅游个性化推荐系统 —— Spring Boot 主启动类。
 *
 * <p>核心模块：
 * <ul>
 *   <li>用户模块：注册 / 登录 / JWT 鉴权 / 角色体系</li>
 *   <li>景点模块：CRUD / 分页筛选 / 周边搜索 / Redis 缓存</li>
 *   <li>推荐引擎：混合评分（偏好 + 热度 + 位置 + 多样性），三层降级兜底</li>
 *   <li>行为记录：浏览 / 点赞 / 收藏，推荐缓存自动失效</li>
 *   <li>行程规划：多天行程管理 + 百度地图智能编排</li>
 *   <li>路线导航：百度地图驾车/公交路线规划 + 周边游览</li>
 *   <li>AI 助手：DeepSeek 流式对话 + 平台景点库约束</li>
 *   <li>管理后台：仪表盘 / 用户管理 / 景点审核</li>
 * </ul></p>
 */
@SpringBootApplication
@MapperScan("com.tripmate.mapper")
public class TripMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripMateApplication.class, args);
    }

    /** 启动时检查环境变量（调试用） */
    @PostConstruct
    public void testEnv() {
        String key = System.getenv("DEEPSEEK_API_KEY");
        System.out.println("Loaded API Key: " + (key != null ? key.substring(0, 10) + "..." : "NULL"));
    }
}
