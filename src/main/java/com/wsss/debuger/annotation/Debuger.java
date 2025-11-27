package com.wsss.debuger.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Debuger注解
 * 用于标记需要被动态代理的Spring Bean
 * 被此注解标记的类在Spring初始化时会被拦截并生成包含bean名称信息的动态代理
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Debuger {
    // 可以添加一些属性来自定义代理行为
    // 暂时留空，仅作为标记用注解
}