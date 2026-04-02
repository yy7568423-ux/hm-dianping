package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author DDDJ
 **/
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 创建RedissonClient对象
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("123456");
        return Redisson.create(config);
    }
//        @Bean
//        public RedissonClient redissonClient2() {
//            // 创建RedissonClient对象
//            Config config = new Config();
//            config.useSingleServer().setAddress("redis://127.0.0.1:6380");
//            return Redisson.create(config);
//        }

//            @Bean
//            public RedissonClient redissonClient3(){
//                // 创建RedissonClient对象
//                Config config = new Config();
//                config.useSingleServer().setAddress("redis://127.0.0.1:6381");
//                return Redisson.create(config);
//
//
//            }
}
