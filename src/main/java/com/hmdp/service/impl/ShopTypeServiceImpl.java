package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //1、从redis查询商铺缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_TYPE_KEY);
        //2、判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            //3、存在，直接返回
            return Result.ok(shopTypeList);
        }
        //4、不存在，根据id查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //5、不存在，返回错误
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺不存在！");
        }
        //6、存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        //7、返回
        return Result.ok(typeList);
    }
}
