package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT; //提前编译脚本,避免每次编译io流影响性能
    static {
        //在静态代码块中初始化
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct //当前类初始化完毕后就执行
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    //线程任务
    private final class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                //1、获取队列中的订单信息
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2、创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }

            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1、获取用户
        Long userId = voucherOrder.getUserId();
        //2、创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" +userId);
        //获取锁
        boolean islock = lock.tryLock(); //无参，不等待
        if (!islock) {
            //获取锁失败，返回失败
            log.error("不允许重复下单");
            return;
        }try{
            proxy.createVoucherOrder(voucherOrder);
        }finally{
            //释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy; //代理对象定义为成员变量，所有线程共享

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取 用户
        Long userId = UserHolder.getUser().getId();
        //1、执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2、判断是否为0，为0则代表有购买资格
        if (result.intValue() != 0) {
            //2.1、不为0，代表无购买资格
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单"); //为1则库存不足，为2则不能重复下单
        }
        //2.2、为0，有购买资格，把下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.2.1、订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.2.2、用户ID
        voucherOrder.setUserId(userId);
        //2.2.3、代金券ID
        voucherOrder.setVoucherId(voucherId);
        //2.2.4、创建阻塞队列
        orderTasks.add(voucherOrder);

        //3、获取代理对象（事务）  //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //4、返回订单信息
        return Result.ok(orderId);
    }



   /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 秒杀券实现
        // 1.查询优惠券ID
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断是否开始活动
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动尚未开始");
        } else if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束");

        }
        //3.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足，返回异常
            return Result.fail("库存不足");
        }
        Long id = UserHolder.getUser().getId();
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);
        //获取锁
        boolean islock = lock.tryLock(); //无参，不等待
        if (!islock) {
            //获取锁失败，返回失败
            return Result.fail("不允许重复下单");
        }try{
        //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally{
            //释放锁
            lock.unlock();
        }
    }*/



    @Transactional
    @Override//两张表的操作，加上事务，出现问题回滚
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //实现一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次");
            return;
        }

//4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        save(voucherOrder);

    }

}
