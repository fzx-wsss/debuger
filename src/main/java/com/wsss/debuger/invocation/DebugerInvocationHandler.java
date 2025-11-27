package com.wsss.debuger.invocation;

import com.wsss.debuger.config.DebugerConfig;
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
     * @param password 认证密码
     * @param methodName 调用的方法名
     * @return 响应数据
     */
    private byte[] sendHttpRequest(String url, byte[] data, String password, String methodName) {
        try {
            java.net.URL requestUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) requestUrl.openConnection();
            
            // 设置请求方法和请求头
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Authorization", "Bearer " + password);
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            conn.setRequestProperty("X-Bean-Name", beanName);
            conn.setRequestProperty("X-Method-Name", methodName);
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
        byte[] bytes = ProtoStuffUtil.serialize(invocation.getArguments());
        String url = debugerConfig.getUrl();
        String methodName = invocation.getMethod().getName();
        
        // bytes数据通过http post请求发送到url地址获取结果
        byte[] responseBytes = sendHttpRequest(url, bytes, debugerConfig.getPassword(), methodName);
        
        // 反序列化响应结果
        if (responseBytes != null && responseBytes.length > 0) {
            return ProtoStuffUtil.deserialize(responseBytes, invocation.getMethod().getReturnType());
        }
        
        return invocation.proceed();
    }
    
    
}