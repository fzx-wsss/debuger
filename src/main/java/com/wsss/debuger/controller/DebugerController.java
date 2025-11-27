package com.wsss.debuger.controller;

import com.wsss.debuger.config.DebugerConfig;
import com.wsss.debuger.model.DebugRequest;
import com.wsss.debuger.model.DebugResponse;
import com.wsss.debuger.utils.ProtoStuffUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Debuger HTTP接口控制器
 * 用于接收远程调试请求，调用本地方法并返回结果
 */
@ConditionalOnProperty(name = "wsss.debuger.mode", havingValue = "server", matchIfMissing = true)
@RestController
@RequestMapping("/debuger")
public class DebugerController implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(DebugerController.class);
    
    private ApplicationContext applicationContext;
    
    @Autowired
    private DebugerConfig debugerConfig;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 处理调试请求的接口
     * @param request HTTP请求对象
     * @return 方法调用结果的序列化数据（DebugResponse对象）
     */
    @PostMapping("/invoke")
    public ResponseEntity<byte[]> invoke(HttpServletRequest request) {
        
        // 1. 读取并反序列化请求数据
        byte[] requestData;
        try {
            requestData = readRequestBody(request);
        } catch (IOException e) {
            logger.error("读取请求体失败", e);
            DebugResponse response = new DebugResponse("读取请求体失败: " + e.getMessage(), e.getClass().getName());
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
        }
        
        if (requestData == null || requestData.length == 0) {
            logger.error("请求体为空");
            DebugResponse response = new DebugResponse("请求体为空", null);
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
        }
        
        DebugRequest debugRequest;
        try {
            // 2. 反序列化为DebugRequest对象
            debugRequest = ProtoStuffUtil.deserialize(requestData, DebugRequest.class);
        } catch (Exception e) {
            logger.error("反序列化请求数据失败", e);
            DebugResponse response = new DebugResponse("反序列化请求数据失败: " + e.getMessage(), e.getClass().getName());
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
        }
        
        // 3. 密码校验
        if (!debugerConfig.getPassword().equals(debugRequest.getPassword())) {
            logger.error("密码校验失败，拒绝请求");
            DebugResponse response = new DebugResponse("密码校验失败，请提供正确的授权信息", null);
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
        }

        
        // 4. 检查必要的参数
        String beanName = debugRequest.getBeanName();
        String methodName = debugRequest.getMethodName();
        if (StringUtils.isEmpty(beanName) || StringUtils.isEmpty(methodName)) {
            logger.error("缺少必要的参数: beanName={}, methodName={}", beanName, methodName);
            DebugResponse response = new DebugResponse("缺少必要的参数，请提供beanName和methodName", null);
            return ResponseEntity.badRequest().body(ProtoStuffUtil.serialize(response));
        }
        
        try {
            // 5. 获取请求参数
            Object[] args = debugRequest.getArguments();
            
            // 6. 从Spring容器获取bean
            Object targetBean = getBeanByName(beanName);
            if (targetBean == null) {
                logger.error("未找到指定的bean: {}", beanName);
                DebugResponse response = new DebugResponse("未找到指定的bean: " + beanName, null);
                return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
            }
            
            // 7. 记录方法调用信息
            logger.info("准备调用目标方法: beanName={}, methodName={}, 参数数量={}", 
                    beanName, methodName, args != null ? args.length : 0);
            
            // 8. 记录执行时间开始
            long startTime = System.currentTimeMillis();
            
            // 9. 查找并调用方法
            Object result = invokeMethod(targetBean, methodName, args);
            
            // 10. 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 11. 构建成功响应
            DebugResponse response = new DebugResponse();
            response.setSuccess(true);
            response.setResult(result);
            response.setExecutionTime(executionTime);
            
            logger.info("方法调用完成: 返回类型={}, 执行时间={}ms", 
                    result != null ? result.getClass().getName() : "null", executionTime);
            
            // 12. 序列化响应并返回
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
            
        } catch (Exception e) {
            logger.error("处理调试请求异常: beanName={}, methodName={}", beanName, methodName, e);
            
            // 构建错误响应
            DebugResponse response = new DebugResponse();
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setExceptionClass(e.getClass().getName());
            
            return ResponseEntity.ok(ProtoStuffUtil.serialize(response));
        }
    }
    
    // 密码校验已移至方法内部实现，不再需要单独的validatePassword方法
    
    /**
     * 读取请求体数据
     * @param request HTTP请求对象
     * @return 请求体数据
     * @throws IOException IO异常
     */
    private byte[] readRequestBody(HttpServletRequest request) throws IOException {
        try (InputStream is = request.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
    
    /**
     * 根据名称从Spring容器获取bean
     * @param beanName bean名称
     * @return bean对象
     */
    private Object getBeanByName(String beanName) {
        try {
            return applicationContext.getBean(beanName);
        } catch (Exception e) {
            logger.warn("获取bean失败: {}", beanName, e);
            return null;
        }
    }
    
    /**
     * 调用指定对象的方法
     * @param target 目标对象
     * @param methodName 方法名
     * @param args 参数数组
     * @return 方法调用结果
     * @throws Exception 调用异常
     */
    private Object invokeMethod(Object target, String methodName, Object[] args) throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("目标对象不能为空");
        }
        
        Class<?> targetClass = target.getClass();
        
        // 查找方法
        Method method = findMethod(targetClass, methodName, args);
        if (method != null) {
            // 设置方法可访问
            method.setAccessible(true);
            // 调用方法
            return method.invoke(target, args);
        } else {
            throw new NoSuchMethodException("未找到方法: " + methodName + " 参数类型: " + getParameterTypes(args));
        }
    }
    
    /**
     * 查找匹配的方法（考虑重载）
     * @param targetClass 目标类
     * @param methodName 方法名
     * @param args 参数数组
     * @return 找到的方法对象
     */
    private Method findMethod(Class<?> targetClass, String methodName, Object[] args) {
        Method[] methods = targetClass.getDeclaredMethods();
        
        // 构建参数类型数组
        Class<?>[] paramTypes = null;
        if (args != null) {
            paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    // 对于null参数，使用Object.class作为类型
                    paramTypes[i] = Object.class;
                } else {
                    paramTypes[i] = args[i].getClass();
                }
            }
        }
        
        // 查找匹配的方法
        for (Method method : methods) {
            if (method.getName().equals(methodName) && isParameterTypesMatch(method.getParameterTypes(), paramTypes)) {
                return method;
            }
        }
        
        // 如果当前类没找到，尝试在父类中查找
        Class<?> superClass = targetClass.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findMethod(superClass, methodName, args);
        }
        
        return null;
    }
    
    /**
     * 检查参数类型是否匹配
     * @param methodParamTypes 方法声明的参数类型
     * @param actualParamTypes 实际参数的类型
     * @return 是否匹配
     */
    private boolean isParameterTypesMatch(Class<?>[] methodParamTypes, Class<?>[] actualParamTypes) {
        // 参数数量检查
        if (methodParamTypes.length != (actualParamTypes != null ? actualParamTypes.length : 0)) {
            return false;
        }
        
        // 参数类型检查
        for (int i = 0; i < methodParamTypes.length; i++) {
            Class<?> methodType = methodParamTypes[i];
            Class<?> actualType = actualParamTypes[i];
            
            // 对于Object类型的参数，任何类型都匹配
            if (methodType == Object.class) {
                continue;
            }
            
            // 对于基本类型，进行装箱类型的匹配
            if (methodType.isPrimitive()) {
                if (!isPrimitiveMatch(methodType, actualType)) {
                    return false;
                }
            } else {
                // 检查类型兼容性（考虑继承关系）
                if (actualType != null && !methodType.isAssignableFrom(actualType)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 检查基本类型与实际类型的匹配
     * @param primitiveType 基本类型
     * @param actualType 实际类型
     * @return 是否匹配
     */
    private boolean isPrimitiveMatch(Class<?> primitiveType, Class<?> actualType) {
        if (actualType == null) {
            return false;
        }
        
        if (primitiveType == int.class && (actualType == Integer.class || actualType == int.class)) {
            return true;
        } else if (primitiveType == long.class && (actualType == Long.class || actualType == long.class)) {
            return true;
        } else if (primitiveType == boolean.class && (actualType == Boolean.class || actualType == boolean.class)) {
            return true;
        } else if (primitiveType == double.class && (actualType == Double.class || actualType == double.class)) {
            return true;
        } else if (primitiveType == float.class && (actualType == Float.class || actualType == float.class)) {
            return true;
        } else if (primitiveType == char.class && (actualType == Character.class || actualType == char.class)) {
            return true;
        } else if (primitiveType == byte.class && (actualType == Byte.class || actualType == byte.class)) {
            return true;
        } else if (primitiveType == short.class && (actualType == Short.class || actualType == short.class)) {
            return true;
        }
        
        return false;
    }
    
    
    
    /**
     * 获取参数类型的字符串表示
     * @param args 参数数组
     * @return 参数类型字符串
     */
    private String getParameterTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.toString(args);
    }
}
