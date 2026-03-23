package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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

         Shop shop = queryMutex(id);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        //7、返回
        return Result.ok(shop);
    }

    public Shop queryMutex(Long id) {
        //1、从redis查询商铺缓存

           String key = CACHE_SHOP_KEY + id;
           String shopJson = stringRedisTemplate.opsForValue().get(key);

           //2、判断是否存在
           if (StrUtil.isNotBlank(shopJson)) {
               //3、存在，直接返回
               Shop shop = JSONUtil.toBean(shopJson, Shop.class);
               return shop;
           }
           //判断是否是空值
           if (shopJson != null) {
               return null;
           }
           //4、实现缓存重建
           //4、1、获取互斥锁
           String lockkey = "lock:shop:" + id;
           Shop shop = null;
           try {
               boolean isLock = tryLock(lockkey);
           //4.2、判断是否获取成功
           // 4.1、失败，休眠10秒再重试
           if (!isLock) {
               Thread.sleep(10);
               queryMutex(id);
           }
           //4.3、成功，返回
           shop = getById(id);

           //5、不存在，返回错误
           if (shop == null) {
               //将空值写入redis
               stringRedisTemplate.opsForValue().set(key,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
               return null;
           }
           //6、存在，写入redis
           stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
       } catch (InterruptedException e) {
           throw new RuntimeException(e);
       }finally {
           //7、释放锁
           unLock(lockkey);
           return shop;

       }



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
