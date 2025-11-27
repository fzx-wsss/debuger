package com.wsss.debuger.processor;

import com.wsss.debuger.annotation.Debuger;
import com.wsss.debuger.config.DebugerConfig;
import com.wsss.debuger.invocation.DebugerInvocationHandler;
import com.wsss.debuger.invocation.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Debuger Bean处理器
 * 拦截带有@Debuger注解或在配置文件debuger.class.name中指定的类
 * 为这些类生成包含bean名称信息的动态代理
 */
@Component
@ConditionalOnProperty(name = "wsss.debuger.mode", havingValue = "client")
public class DebugerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DebugerBeanPostProcessor.class);

    @Autowired
    private DebugerConfig debugerConfig;
    private Set<String> beanNames = new HashSet<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
    
    /**
     * 检查类实现的接口上是否有Debuger注解
     * @param beanClass 要检查的类
     * @return 如果任何接口上有Debuger注解则返回true，否则返回false
     */
    private boolean hasInterfaceWithDebugerAnnotation(Class<?> beanClass) {
        for (Class<?> intf : beanClass.getInterfaces()) {
            if (intf.isAnnotationPresent(Debuger.class)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否是FactoryBean且创建的对象类型上有Debuger注解
     * @param bean 要检查的bean
     * @return 如果是FactoryBean且创建的对象类型上有Debuger注解则返回true，否则返回false
     */
    private boolean isFactoryBeanWithDebugerTarget(Object bean) {
        if (bean instanceof FactoryBean<?>) {
            try {
                // 获取FactoryBean创建的对象类型
                Class<?> objectType = ((FactoryBean<?>) bean).getObjectType();
                if (objectType != null) {
                    // 检查对象类型上是否有Debuger注解
                    return objectType.isAnnotationPresent(Debuger.class) || hasInterfaceWithDebugerAnnotation(objectType);
                }
            } catch (Exception e) {
                // 如果获取对象类型失败，记录日志但不影响正常流程
                logger.debug("获取FactoryBean对象类型失败: beanName={}", bean.getClass().getName(), e);
            }
        }
        return false;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        if (bean instanceof FactoryBean<?>) {
            return bean;
        }

        // 检查是否需要代理：1. 有@Debuger注解 2. 接口上有@Debuger注解 3. 是工厂类且创建的对象有Debuger注解 4. 在配置列表中
        boolean needProxy = beanClass.isAnnotationPresent(Debuger.class)
                || hasInterfaceWithDebugerAnnotation(beanClass)
                || isFactoryBeanWithDebugerTarget(bean)
                || debugerConfig.getClassNames().contains(beanClass)
                || debugerConfig.getBeanNames().contains(beanName);

        if (needProxy) {
            logger.info("为Bean生成动态代理: beanName={}", beanName);
            return Proxy.getProxy(bean, new DebugerInvocationHandler(beanName, debugerConfig));
        }
        
        return bean;
    }
    

}