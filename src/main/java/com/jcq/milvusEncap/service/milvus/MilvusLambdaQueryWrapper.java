package com.jcq.milvusEncap.service.milvus;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.jcq.milvusEncap.dal.dataobject.agent.SamplesDO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Milvus Lambda查询构造器，类似MyBatis的LambdaQueryWrapper
 * 支持等于、不等于、大于、小于、in、模糊查询等基础操作
 *
 * <p>开发这个功能的时候还没有发现比较成熟的框架，所以先手写了下面几个方法，等框架成熟之后，可以直接引用框架</p
 *
 * @param <T> 实体类类型
 *
 * @author : jucunqi
 * @since : 2025/10/16
 */
public class MilvusLambdaQueryWrapper<T> {

    // 存储查询条件，格式如 "id > 100"、"name like '%test%'"
    private final List<String> conditions = new ArrayList<>();

    /**
     * 等于条件 (field = value)
     * @param column 字段Lambda表达式，如 User::getId
     * @param value 值
     * @return 自身实例，支持链式调用
     */
    public <R> MilvusLambdaQueryWrapper<T> eqIfPresent(SFunction<T, R> column, Object value) {
        if (value == null) {
            return this;
        }
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, "==", value));
        return this;
    }

    /**
     * 不等于条件 (field != value)
     * @param column 字段Lambda表达式
     * @param value 值
     * @return 自身实例
     */
    public <R> MilvusLambdaQueryWrapper<T> ne(SFunction<T, R> column, Object value) {
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, "!=", value));
        return this;
    }

    /**
     * 大于条件 (field > value)
     * @param column 字段Lambda表达式
     * @param value 值
     * @return 自身实例
     */
    public <R extends Comparable<?>> MilvusLambdaQueryWrapper<T> gt(SFunction<T, R> column, R value) {
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, ">", value));
        return this;
    }

    /**
     * 大于等于条件 (field >= value)
     * @param column 字段Lambda表达式
     * @param value 值
     * @return 自身实例
     */
    public <R extends Comparable<?>> MilvusLambdaQueryWrapper<T> ge(SFunction<T, R> column, R value) {
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, ">=", value));
        return this;
    }

    /**
     * 小于条件 (field < value)
     * @param column 字段Lambda表达式
     * @param value 值
     * @return 自身实例
     */
    public <R extends Comparable<?>> MilvusLambdaQueryWrapper<T> lt(SFunction<T, R> column, R value) {
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, "<", value));
        return this;
    }

    /**
     * 小于等于条件 (field <= value)
     * @param column 字段Lambda表达式
     * @param value 值
     * @return 自身实例
     */
    public <R extends Comparable<?>> MilvusLambdaQueryWrapper<T> le(SFunction<T, R> column, R value) {
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, "<=", value));
        return this;
    }

    /**
     * in条件 (field in (v1, v2, ...))
     * @param column 字段Lambda表达式
     * @param values 值列表
     * @return 自身实例
     */
    @SafeVarargs
    public final <R> MilvusLambdaQueryWrapper<T> in(SFunction<T, R> column, R... values) {
        return in(column, Arrays.asList(values));
    }

    /**
     * in条件 (field in (v1, v2, ...))
     * @param column 字段Lambda表达式
     * @param values 值列表
     * @return 自身实例
     */
    public <R> MilvusLambdaQueryWrapper<T> in(SFunction<T, R> column, List<R> values) {
        String columnName = getColumnName(column);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("in条件的值列表不能为空");
        }

        StringBuilder inClause = new StringBuilder();
        inClause.append(columnName).append(" in [");

        for (int i = 0; i < values.size(); i++) {
            inClause.append(formatValue(values.get(i)));
            if (i < values.size() - 1) {
                inClause.append(", ");
            }
        }
        inClause.append("]");

        conditions.add(inClause.toString());
        return this;
    }

    /**
     * 模糊查询 (field like '%value%')
     * Milvus的like语法为 field like "%value%"
     * @param column 字段Lambda表达式
     * @param value 模糊匹配值
     * @return 自身实例
     */
    public MilvusLambdaQueryWrapper<T> likeIfPresent(SFunction<T, String> column, String value) {

        if (StrUtil.isEmpty(value)) {
            return this;
        }
        String columnName = getColumnName(column);
        conditions.add(String.format("%s like \"%%%s%%\"", columnName, value));
        return this;
    }

    /**
     * 拼接查询条件，生成Milvus兼容的过滤字符串
     * @return 过滤条件字符串，如 "id > 100 and name like '%test%'"
     */
    public String buildFilter() {
        if (conditions.isEmpty()) {
            return "";
        }
        // Milvus的多个条件用 and 连接
        return String.join(" and ", conditions);
    }

    /**
     * 从Lambda表达式中获取字段名
     * @param column 字段Lambda表达式，如 User::getName
     * @return 字段名
     */
    private <R> String getColumnName(SFunction<T, R> column) {

        // 利用Mybatis-plus的工具类解析属性
        String methodName = LambdaUtils.extract(column).getImplMethodName();

        // 解析getter方法名为字段名
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return CharSequenceUtil.toSymbolCase(StrUtil.lowerFirst(methodName.substring(3)), '_');
        } else if (methodName.startsWith("is") && methodName.length() > 2) { // 处理布尔类型的getter方法
            return CharSequenceUtil.toSymbolCase(StrUtil.lowerFirst(methodName.substring(2)), '_');
        }

        throw new IllegalArgumentException("无效的字段表达式: " + methodName);
    }

    /**
     * 构建条件字符串
     * @param columnName 字段名
     * @param operator 操作符
     * @param value 值
     * @return 条件字符串，如 "age >= 18"
     */
    private String buildCondition(String columnName, String operator, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("查询条件的值不能为null");
        }
        return columnName + " " + operator + " " + formatValue(value);
    }

    /**
     * 格式化值，为字符串添加引号
     * @param value 要格式化的值
     * @return 格式化后的值，如字符串"test" -> "\"test\""，数字123 -> "123"
     */
    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value.toString() + "\"";
        }
        return value.toString();
    }

    public static void main(String[] args) {
        SFunction<SamplesDO, String> getAgentName = SamplesDO::getAgentName;
        String implMethodName = LambdaUtils.extract(getAgentName).getImplMethodName();
        System.out.println(implMethodName);
    }
}
