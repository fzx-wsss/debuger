package com.wsss.debuger.invocation;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

public class Proxy {
    public static Object getProxy(Object target, MethodInterceptor advice) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(advice);
        return proxyFactory.getProxy();
    }
}
