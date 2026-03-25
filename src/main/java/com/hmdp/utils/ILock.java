package com.hmdp.utils;

/**
 * @author DDDJ
 **/
public interface ILock {
    // 尝试获取锁 timeoutSec 锁持有的时间 true 获取成功 false 获取失败
    boolean tryLock(long timeoutSec);
    void unlock();
}
