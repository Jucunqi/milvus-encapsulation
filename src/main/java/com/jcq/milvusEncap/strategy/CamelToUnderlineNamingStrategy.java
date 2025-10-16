package com.jcq.milvusEncap.strategy;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.FieldNamingStrategy;

import java.lang.reflect.Field;

/**
 * Gson 自定义字段命名策略：将 Java 驼峰字段名转为下划线 JSON 属性名（基于 CharSequenceUtil.toSymbolCase）
 */
public class CamelToUnderlineNamingStrategy implements FieldNamingStrategy {

    @Override
    public String translateName(Field field) {
        // 1. 获取 Java 实体的原始字段名（如 "userName"）
        String originalFieldName = field.getName();
        // 2. 调用 CharSequenceUtil.toSymbolCase 转为下划线名（如 "userName" → "user_name"）
        // 3. 返回转换后的名称，作为 JSON 属性名
        return CharSequenceUtil.toSymbolCase(originalFieldName, '_');
    }
}
