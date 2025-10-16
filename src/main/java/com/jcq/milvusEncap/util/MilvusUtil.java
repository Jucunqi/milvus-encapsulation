package com.jcq.milvusEncap.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.jcq.milvusEncap.annotation.PrimaryKey;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MilvusUtil {

    /**
     * 静态方法：解析对象中带有 @PrimaryKey 注解的字段，并返回该字段的值（支持 Long 类型主键）
     *
     * @param entity 待解析的实体对象（不能为 null）
     * @return 主键字段的值（Long 类型，若字段是 int/Integer，会自动装箱为 Long）
     * @throws IllegalArgumentException 若对象为 null、无主键、多主键、字段类型不兼容时抛出
     * @throws IllegalAccessException   若主键字段无访问权限（private 未处理）时抛出
     */
    public static Long getPrimaryKeyValue(Object entity) throws IllegalAccessException {

        // 1. 校验对象非 null
        if (entity == null) {
            throw new IllegalArgumentException("实体对象不能为 null，无法解析 @PrimaryKey 注解");
        }

        Class<?> entityClass = entity.getClass();
        Field primaryKeyField = null; // 存储找到的主键字段

        // 2. 扫描对象的所有字段（包括父类的字段，支持继承）
        Class<?> currentClass = entityClass;
        while (currentClass != null) { // 循环遍历父类，直到 Object 类
            Field[] fields = currentClass.getDeclaredFields(); // 获取当前类的所有字段（包括 private）
            for (Field field : fields) {
                // 判断字段是否标注 @PrimaryKey 注解
                if (field.isAnnotationPresent(PrimaryKey.class)) {
                    // 校验是否存在多个主键（不允许）
                    if (primaryKeyField != null) {
                        throw new IllegalArgumentException(
                                String.format("实体类[%s]存在多个 @PrimaryKey 注解字段：[%s] 和 [%s]，仅允许一个主键",
                                        entityClass.getName(),
                                        primaryKeyField.getName(),
                                        field.getName())
                        );
                    }
                    primaryKeyField = field; // 记录找到的主键字段
                }
            }
            currentClass = currentClass.getSuperclass(); // 继续扫描父类
        }

        // 3. 校验是否找到主键字段
        if (primaryKeyField == null) {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]未找到 @PrimaryKey 注解字段，请为主键字段添加该注解",
                            entityClass.getName())
            );
        }

        // 4. 读取主键字段的值（处理 private 字段的访问权限）
        primaryKeyField.setAccessible(true); // 突破访问权限限制（即使字段是 private）
        Object primaryKeyValue = primaryKeyField.get(entity); // 获取字段值

        // 5. 校验并转换主键值为 Long 类型（支持 int/Integer/Long 等常用数值类型）
        return convertToLong(primaryKeyValue, entityClass.getName(), primaryKeyField.getName());
    }

    /**
     * 辅助方法：将主键值转换为 Long 类型（处理常见数值类型兼容）
     *
     * @param value           原始主键值
     * @param entityClassName 实体类名（用于异常信息）
     * @param fieldName       主键字段名（用于异常信息）
     * @return 转换后的 Long 类型主键值
     * @throws IllegalArgumentException 若类型不兼容或值为 null 时抛出
     */
    private static Long convertToLong(Object value, String entityClassName, String fieldName) {
        // 校验主键值非 null
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]的主键字段[%s]值为 null，无法获取主键",
                            entityClassName, fieldName)
            );
        }

        // 支持的主键类型：Long、Integer、int（其他类型需扩展可在此处添加）
        Class<?> valueClass = value.getClass();
        if (valueClass == Long.class) {
            return (Long) value;
        } else if (valueClass == Integer.class) {
            return ((Integer) value).longValue(); // Integer 转 Long
        } else {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]的主键字段[%s]类型不支持（当前类型：%s），仅支持 Long/Integer/int",
                            entityClassName, fieldName, valueClass.getName())
            );
        }
    }

    /**
     * 将对象中@PrimaryKey注解type为auto的字段值设置为null
     *
     * @param obj 要处理的对象
     * @throws IllegalAccessException 如果字段访问权限不足时抛出
     */
    public static void setAutoPrimaryKeyToNull(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return;
        }

        // 获取对象的Class实例
        Class<?> clazz = obj.getClass();

        // 遍历所有声明的字段（包括私有字段）
        for (Field field : clazz.getDeclaredFields()) {
            // 检查字段是否带有@PrimaryKey注解
            if (field.isAnnotationPresent(PrimaryKey.class)) {
                // 获取注解实例
                PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);

                // 检查注解的type属性是否为"auto"
                if ("auto".equals(primaryKey.type())) {
                    // 设置字段可访问（即使是private修饰的）
                    field.setAccessible(true);

                    // 将字段值设置为null
                    field.set(obj, null);

                    // 恢复字段的可访问性
                    field.setAccessible(false);
                }
            }
        }
    }


    /**
     * 获取指定类及其父类中带有@PrimaryKey注解的属性名（唯一）
     *
     * @param clazz 要检查的类
     * @return 带有@PrimaryKey注解的属性名（处理后格式）
     * @throws IllegalArgumentException 当存在多个主键字段时抛出
     */
    public static String getPrimaryKeyFieldName(Class<?> clazz) {
        List<PrimaryKeyField> primaryKeyFields = new ArrayList<>();

        // 递归收集所有带@PrimaryKey注解的字段（包含父类）
        collectPrimaryKeyFields(clazz, primaryKeyFields);

        // 检查是否存在多个主键
        if (primaryKeyFields.size() > 1) {
            throw new IllegalArgumentException(
                    String.format("类[%s]及其父类中存在多个@PrimaryKey注解的字段：%s",
                            clazz.getName(),
                            primaryKeyFields.stream()
                                    .map(PrimaryKeyField::getFieldName)
                                    .toList()
                    )
            );
        }

        // 检查是否存在主键
        if (primaryKeyFields.isEmpty()) {
            throw new IllegalArgumentException("未找到@PrimaryKey注解的字段");
        }

        // 处理返回格式（驼峰转下划线，若type为auto）
        PrimaryKeyField field = primaryKeyFields.get(0);
        return "auto".equals(field.getAnnotation().type())
                ? CharSequenceUtil.toSymbolCase(field.getFieldName(), '_')
                : field.getFieldName();
    }

    /**
     * 递归收集类及其父类中所有带@PrimaryKey注解的字段
     */
    private static void collectPrimaryKeyFields(Class<?> clazz, List<PrimaryKeyField> result) {
        // 终止条件：已递归到Object类
        if (clazz == null || clazz.equals(Object.class)) {
            return;
        }

        // 收集当前类的主键字段
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(PrimaryKey.class)) {
                result.add(new PrimaryKeyField(
                        field.getName(),
                        field.getAnnotation(PrimaryKey.class)
                ));
            }
        }

        // 递归处理父类
        collectPrimaryKeyFields(clazz.getSuperclass(), result);
    }

    /**
     * 内部辅助类：存储主键字段名和对应的注解实例
     */
    @Getter
    private static class PrimaryKeyField {
        private final String fieldName;
        private final PrimaryKey annotation;

        public PrimaryKeyField(String fieldName, PrimaryKey annotation) {
            this.fieldName = fieldName;
            this.annotation = annotation;
        }

    }
}
