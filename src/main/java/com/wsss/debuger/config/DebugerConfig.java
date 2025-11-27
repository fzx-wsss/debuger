package com.wsss.debuger.config;

import com.wsss.debuger.processor.DebugerBeanPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Debuger Spring配置类
 * 负责注册DebugerBeanPostProcessor并启用拦截器功能
 */
@Configuration
public class DebugerConfig {

    private static final Logger logger = LoggerFactory.getLogger(DebugerConfig.class);

    @Value("${wsss.debuger.class.names:}")
    private List<String> classNames;
    private Set<Class> classs;
    @Value("${wsss.debuger.bean.names:}")
    private Set<String> beanNames;


    @Value("${wsss.debuger.proxy.enable:false}")
    private boolean enable;
    @Value("${wsss.debuger.proxy.password:}")
    private String password;
    @Value("${wsss.debuger.proxy.url:}")
    private String url;

    public Set<Class> getClassNames() {
        // 检查classs和classNames是否一致
        if (classs == null) {
            // 创建新的classs列表
            Set<Class> classSet = new HashSet<>(classNames.size());
            // 遍历classNames，加载对应的类到classs中
            for (String className : classNames) {
                if (className != null && !className.trim().isEmpty()) {
                    try {
                        Class<?> clazz = Class.forName(className.trim());
                        classSet.add(clazz);
                        logger.info("已加载类: {}", className.trim());
                    } catch (ClassNotFoundException e) {
                        logger.error("加载类失败: {}", className.trim(), e);
                    }
                }
            }
            logger.info("类加载完成，成功加载 {} 个类", classSet.size());
            classs = classSet;
        }

        return classs;
    }

    public Set<String> getBeanNames() {
        return beanNames;
    }

    public boolean isEnable() {
        return enable;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }
}