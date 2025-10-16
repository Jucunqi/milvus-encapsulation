package com.jcq.milvusEncap.annotation;

import java.lang.annotation.*;

/**
 * 用于Milvus实体类上，表明Collection名称
 *
 * @author : jucunqi
 * @since : 2025/10/14
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface CollectionName {

    String value() default "";
}
