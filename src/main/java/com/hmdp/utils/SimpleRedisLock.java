package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author DDDJ
 **/
public class SimpleRedisLock implements ILock {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private String name; // 锁的key
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString().replace("-", "");


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程id
        String clientId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, clientId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag); //避免拆箱有空指针风险
    }

    @Override
    public void unlock() {
        // 获取线程id
        String clientId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
        //判断是否是当前线程的锁
        if (clientId.equals(id)) {
            //一致 释放锁
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }


    }
}
