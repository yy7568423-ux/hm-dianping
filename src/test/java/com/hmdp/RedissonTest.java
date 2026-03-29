package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author DDDJ
 **/
@Slf4j
@SpringBootTest
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;

    private RLock lock;

    @BeforeEach
    void setUp() {
        // 获取锁
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");

        //创建联锁multilock
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }

    @Test
    void test1() throws InterruptedException {
        boolean isLock = lock.tryLock(10L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败");
            return;
        }
        try {
            log.info("获取锁成功");
            method2();
            log.info("执行业务");
        } finally {
            log.warn("准备释放锁");
            lock.unlock();
        }
    }
    private void method2() {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败2");
            return;
        }
        try {
            log.info("获取锁成功2");
            log.info("执行方法2");
            method3();
        } finally {
            log.warn("准备释放锁2");
            lock.unlock();
        }
    }
    private void method3() {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败3");
            return;
        }
        try {
            log.info("获取锁成功3");
            log.info("执行方法3");
        }
        finally {
            log.warn("准备释放锁3");
            lock.unlock();
        }
    }
}
