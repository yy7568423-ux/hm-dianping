package com.hmdp.utils;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author DDDJ
 **/
@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
        log.debug("写入缓存：{}", key);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R , ID> R queryWithPassThrough(String keyPrefix, ID  id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1、从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3、存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断是否是空值
        if (json != null) {
            return null;
        }
        //4、不存在，根据id查询数据库
        R r = (R) dbFallback.apply(id);

        //5、不存在，返回错误
        if (r == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL , TimeUnit.MINUTES);
            return null;
        }
        //6、存在，写入redis
        this.set(key, r, time, unit);

        //7、返回
        return r;
    }





    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit) {
        String key = keyPrefix + id; // Fixed variable name
        //逻辑过期解决缓存穿透
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(json)) {
            return null;
        }
        //2. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //3. 判断是否过期

        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return (R) r;
        }
        //4. 已过期
        //4. 重建缓存
        //4.1 获取互斥锁
        String lockkey = LOCK_SHOP_KEY+ id;
        boolean isLock = tryLock(lockkey);
        if (isLock) {
            //5.1 获取锁成功，开启独立线程进行重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, 20L, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //6. 释放锁
                    unLock(lockkey);
                }

            });

        }
        //返回过期店铺信息
        return r;
    }


    //获取锁 互斥锁解决缓存击穿问题
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1" , 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }
    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
