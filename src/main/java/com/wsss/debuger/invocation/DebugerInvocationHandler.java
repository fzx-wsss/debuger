package com.wsss.debuger.invocation;

import com.wsss.debuger.config.DebugerConfig;
import com.wsss.debuger.model.DebugRequest;
import com.wsss.debuger.model.DebugResponse;
import com.wsss.debuger.utils.ProtoStuffUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Debuger方法调用处理器
 */
public class DebugerInvocationHandler implements MethodInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(DebugerInvocationHandler.class);
    
    // Spring中的Bean名称
    private final String beanName;
    // Debuger配置
    private final DebugerConfig debugerConfig;


    /**
     * 构造函数（带配置）
     * @param beanName Spring中的Bean名称
     * @param debugerConfig Debuger配置
     */
    public DebugerInvocationHandler(String beanName, DebugerConfig debugerConfig) {
        this.beanName = beanName;
        this.debugerConfig = debugerConfig;
    }
    
    /**
     * 发送HTTP POST请求
     * @param url 请求地址
     * @param data 请求数据
     * @return 响应数据
     */
    private byte[] sendHttpRequest(String url, byte[] data) {
        try {
            java.net.URL requestUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) requestUrl.openConnection();
            
            // 设置请求方法和请求头
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            
            // 发送数据
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(data);
                os.flush();
            }
            
            // 检查响应状态
            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                // 读取响应数据
                try (java.io.InputStream is = conn.getInputStream();
                     java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    return baos.toByteArray();
                }
            } else {
                logger.error("HTTP请求失败，响应码: {}", responseCode);
            }
            
            conn.disconnect();
        } catch (Exception e) {
            logger.error("发送HTTP请求异常: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (Object.class.equals(invocation.getMethod().getDeclaringClass())
            || !debugerConfig.isEnable()) {
            return invocation.proceed();
        }
        if(debugerConfig.getPassword() == null || debugerConfig.getPassword().trim().isEmpty()) {
            throw new UnsupportedOperationException("debuger.password is empty");
        }
        
        // 创建DebugRequest对象，包含所有必要信息
        DebugRequest request = new DebugRequest(
            beanName,
            invocation.getMethod().getName(),
            invocation.getArguments(),
            debugerConfig.getPassword()
        );
        
        // 序列化请求对象
        byte[] bytes = ProtoStuffUtil.serialize(request);
        String url = debugerConfig.getUrl();
        
        logger.info("发送调试请求: {}", request);
        
        // 发送HTTP请求
        byte[] responseBytes = sendHttpRequest(url, bytes);
        
        // 反序列化响应结果
        if (responseBytes != null && responseBytes.length > 0) {
            DebugResponse response = ProtoStuffUtil.deserialize(responseBytes, DebugResponse.class);
            
            if (response.isSuccess()) {
                // 调用成功，返回结果
                logger.info("调试响应成功: 执行时间={}ms", response.getExecutionTime());
                return response.getResult();
            } else {
                // 调用失败，抛出异常
                logger.error("调试响应失败: {} - {}", response.getExceptionClass(), response.getErrorMessage());
                // 尝试根据异常类名创建并抛出异常
                if (response.getExceptionClass() != null) {
                    try {
                        Class<?> exceptionClass = Class.forName(response.getExceptionClass());
                        if (Throwable.class.isAssignableFrom(exceptionClass)) {
                            Throwable exception = (Throwable) exceptionClass.getDeclaredConstructor(String.class).newInstance(
                                response.getErrorMessage());
                            throw exception;
                        }
                    } catch (Exception e) {
                        // 如果无法创建指定异常类，则抛出通用异常
                        throw new RuntimeException(response.getErrorMessage());
                    }
                } else {
                    throw new RuntimeException(response.getErrorMessage());
                }
            }
        }
        
        logger.warn("未收到调试响应，执行本地方法");
        return invocation.proceed();
    }
    
    
}