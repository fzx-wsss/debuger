package com.wsss.debuger.utils;

import com.wsss.debuger.config.DebugerConfig;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.IdStrategy;
import io.protostuff.runtime.RuntimeSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.ObjenesisStd;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtoStuffUtil {
    private static final Logger log = LoggerFactory.getLogger(DebugerConfig.class);
    private static Map<Class,Schema> cache = new ConcurrentHashMap<>();
    private static IdStrategy strategy = new DefaultIdStrategy(IdStrategy.DEFAULT_FLAGS
            | IdStrategy.COLLECTION_SCHEMA_ON_REPEATED_FIELDS | IdStrategy.MORPH_NON_FINAL_POJOS,null,0);
    private static Objenesis objenesis = new ObjenesisStd(true);
    private static ThreadLocal<LinkedBuffer> threadLocal = new ThreadLocal<LinkedBuffer>() {
        @Override
        protected LinkedBuffer initialValue() {
            return LinkedBuffer.allocate(4*1024);
        }
    };
    /**
     * 序列化对象
     *
     * @param obj
     * @return
     */
    public static <T> byte[] serialize(T obj) {
        if (obj == null) {
            log.error("Failed to serializer, obj is null");
            throw new RuntimeException("Failed to serializer");
        }

        @SuppressWarnings("unchecked")
        Schema<T> schema = getSchema(obj.getClass());
        LinkedBuffer buffer = threadLocal.get();
        buffer.clear();
        byte[] protoStuff;
        try {
            protoStuff = ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            log.error("Failed to serializer, obj:{}", obj, e);
            throw new RuntimeException("Failed to serializer");
        } finally {
            buffer.clear();
        }
        return protoStuff;
    }

    private static Schema getSchema(Class clazz) {
        Schema schema = cache.get(clazz);
        if(schema == null) {
            schema = cache.computeIfAbsent(clazz, c->RuntimeSchema.getSchema(c,strategy));
        }
        return schema;
//        return RuntimeSchema.getSchema(clazz);
    }

    /**
     * 序列化对象
     *
     * @param obj
     * @return
     */
    public static <T> int serialize(T obj, OutputStream outputStream) {
        if (obj == null) {
            log.error("Failed to serializer, obj is null");
            throw new RuntimeException("Failed to serializer");
        }

        @SuppressWarnings("unchecked") Schema<T> schema = getSchema(obj.getClass());
        LinkedBuffer buffer = threadLocal.get();
        buffer.clear();
        try {
            return ProtostuffIOUtil.writeTo(outputStream,obj,schema,buffer);
        } catch (Exception e) {
            log.error("Failed to serializer, obj:{}", obj, e);
            throw new RuntimeException("Failed to serializer");
        } finally {
            buffer.clear();
        }
    }

    /**
     * 反序列化对象
     *
     * @param inputStream
     * @param targetClass
     * @return
     */
    public static <T> T deserialize(InputStream inputStream, Class<T> targetClass) {
        try {
            T instance = (T) objenesis.newInstance(targetClass);
            Schema<T> schema = getSchema(targetClass);
            ProtostuffIOUtil.mergeFrom(inputStream, instance, schema);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize",e);
        }
    }
    /**
     * 反序列化对象
     *
     * @param paramArrayOfByte
     * @param targetClass
     * @return
     */
    public static <T> T deserialize(byte[] paramArrayOfByte, Class<T> targetClass) {
        if (paramArrayOfByte == null || paramArrayOfByte.length == 0) {
            log.error("Failed to deserialize, byte is empty");
            throw new RuntimeException("Failed to deserialize");
        }

        T instance = (T) objenesis.newInstance(targetClass);
        Schema<T> schema = getSchema(targetClass);
        ProtostuffIOUtil.mergeFrom(paramArrayOfByte, instance, schema);
        return instance;
    }

    /**
     * 序列化列表
     *
     * @param objList
     * @return
     */
    public static <T> byte[] serializeList(List<T> objList) {
        if (objList == null || objList.isEmpty()) {
            log.error("Failed to serializer, objList is empty");
            throw new RuntimeException("Failed to serializer");
        }

        @SuppressWarnings("unchecked") Schema<T> schema = getSchema(objList.get(0).getClass());
        LinkedBuffer buffer = threadLocal.get();
        buffer.clear();
        byte[] protoStuff;
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            ProtostuffIOUtil.writeListTo(bos, objList, schema, buffer);
            protoStuff = bos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to serializer, obj list:{}", objList, e);
            throw new RuntimeException("Failed to serializer");
        } finally {
            buffer.clear();
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return protoStuff;
    }

    /**
     * 反序列化列表
     *
     * @param paramArrayOfByte
     * @param targetClass
     * @return
     */
    public static <T> List<T> deserializeList(byte[] paramArrayOfByte, Class<T> targetClass) {
        if (paramArrayOfByte == null || paramArrayOfByte.length == 0) {
            log.error("Failed to deserialize, byte is empty");
            throw new RuntimeException("Failed to deserialize");
        }

        Schema<T> schema = getSchema(targetClass);
        List<T> result;
        try {
            result = ProtostuffIOUtil.parseListFrom(new ByteArrayInputStream(paramArrayOfByte), schema);
        } catch (IOException e) {
            log.error("Failed to deserialize", e);
            throw new RuntimeException("Failed to deserialize");
        }
        return result;
    }
}