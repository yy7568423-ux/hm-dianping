package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {

//         Shop shop = queryMutex(id);
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        //7、返回
        return Result.ok(shop);
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //逻辑过期解决缓存穿透
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //2. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //3. 判断是否过期

        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return shop;
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
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //6. 释放锁
                    unLock(lockkey);
                }

            });

        }
        //返回过期店铺信息
        return shop;
    }


    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    public Shop queryMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }

        // 2. 缓存重建
        String lockkey = "lock:shop:" + id;
        boolean isLock = tryLock(lockkey);
        Shop shop = null;
        // 提升作用域

        try {
            if (!isLock) {
                Thread.sleep(50);
                return queryMutex(id);
            }
            shop = getById(id);

            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockkey);
        }
        return shop;
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

    @Override
    @Transactional
    public Object update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1、更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
