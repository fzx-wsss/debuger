package com.wsss.debuger.controller;

import com.wsss.debuger.config.DebugerConfig;
import com.wsss.debuger.utils.ProtoStuffUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Debuger HTTP接口控制器
 * 用于接收远程调试请求，调用本地方法并返回结果
 */
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
     * @param authorization 授权信息（密码）
     * @param beanName 要调用的bean名称
     * @param methodName 要调用的方法名
     * @return 方法调用结果的序列化数据
     */
    @PostMapping("/invoke")
    public ResponseEntity<byte[]> invoke(HttpServletRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestHeader(value = "X-Bean-Name", required = false) String beanName,
                                        @RequestHeader(value = "X-Method-Name", required = false) String methodName) {
        
        // 1. 密码校验
        if (!validatePassword(authorization)) {
            logger.error("密码校验失败，拒绝请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("密码校验失败，请提供正确的授权信息".getBytes(StandardCharsets.UTF_8));
        }
        
        // 2. 检查必要的参数
        if (StringUtils.isEmpty(beanName) || StringUtils.isEmpty(methodName)) {
            logger.error("缺少必要的参数: beanName={}, methodName={}", beanName, methodName);
            return ResponseEntity.badRequest()
                    .body("缺少必要的参数，请提供beanName和methodName".getBytes(StandardCharsets.UTF_8));
        }
        
        try {
            // 3. 获取请求参数
            byte[] requestData = readRequestBody(request);
            Object[] args = null;
            if (requestData != null && requestData.length > 0) {
                args = ProtoStuffUtil.deserialize(requestData, Object[].class);
            }
            
            // 4. 从Spring容器获取bean
            Object targetBean = getBeanByName(beanName);
            if (targetBean == null) {
                logger.error("未找到指定的bean: {}", beanName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(("未找到指定的bean: " + beanName).getBytes(StandardCharsets.UTF_8));
            }
            
            // 5. 查找并调用方法
            logger.info("准备调用目标方法: beanName={}, methodName={}, 参数类型={}", 
                    beanName, methodName, Arrays.stream(args).map(arg -> arg != null ? arg.getClass().getName() : "null").toArray());
            Object result = invokeMethod(targetBean, methodName, args);
            logger.info("方法调用完成: 返回类型={}, 是否为null={}", 
                    result != null ? result.getClass().getName() : "null", result == null);
            
            // 6. 序列化结果并返回
            return ResponseEntity.ok(ProtoStuffUtil.serialize(result));
            
        } catch (Exception e) {
            logger.error("处理调试请求异常: beanName={}, methodName={}", beanName, methodName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("处理调试请求异常: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * 校验密码
     * @param authorization 授权信息
     * @return 是否校验通过
     */
    private boolean validatePassword(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || StringUtils.isEmpty(debugerConfig.getPassword())) {
            return false;
        }
        String password = authorization.substring(7);
        return debugerConfig.getPassword().equals(password);
    }
    
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
