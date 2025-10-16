package com.jcq.milvusEncap.annotation;

import java.lang.annotation.*;

/**
 * 用于Milvus实体类的属性上，表明主键
 *
 * @author : jucunqi
 * @since : 2025/10/14
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface PrimaryKey {

    String value() default "";
    String type() default "auto";
}
