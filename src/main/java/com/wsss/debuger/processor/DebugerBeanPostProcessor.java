package com.wsss.debuger.processor;

import com.wsss.debuger.annotation.Debuger;
import com.wsss.debuger.config.DebugerConfig;
import com.wsss.debuger.invocation.DebugerInvocationHandler;
import com.wsss.debuger.invocation.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
/**
 * Debuger Bean处理器
 * 拦截带有@Debuger注解或在配置文件debuger.class.name中指定的类
 * 为这些类生成包含bean名称信息的动态代理
 */
@Component
public class DebugerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DebugerBeanPostProcessor.class);

    @Autowired
    private DebugerConfig debugerConfig;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 不需要在初始化前做处理，直接返回原bean
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 检查是否需要代理：1. 有@Debuger注解 2. 在配置列表中
        boolean needProxy = beanClass.isAnnotationPresent(Debuger.class)
                || debugerConfig.getClassNames().contains(beanClass);
        
        if (needProxy) {
            logger.info("为Bean生成动态代理: beanName={}", beanName);
            return Proxy.getProxy(bean, new DebugerInvocationHandler(beanName, debugerConfig));
        }
        
        return bean;
    }
    

}