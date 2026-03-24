package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author DDDJ
 **/
@Component
public class RedisIdWorker {
    //初始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    //序列号位数 定义成常量方便修改
    private static final long COUNT_BITS = 32;

    @Resource
    public StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1 获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 自增长
        long seq = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);


        //3、拼接返回 位运算，让时间戳左移32位，后或运算拼接序列号
        return timestamp << COUNT_BITS | seq;
    }

}
