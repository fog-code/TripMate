package com.tripmate.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>在插入或更新数据时自动填充时间戳字段，无需手动设置：
 * <ul>
 *   <li>插入时：填充 {@code createdAt} 和 {@code updatedAt}</li>
 *   <li>更新时：填充 {@code updatedAt}</li>
 * </ul>
 * 实体中需配合 {@code @TableField(fill = ...)} 注解使用。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
