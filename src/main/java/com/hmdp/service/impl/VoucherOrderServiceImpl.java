package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional //两张表的操作，加上事务，出现问题回滚
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
        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        Long id = UserHolder.getUser().getId();
        voucherOrder.setUserId(id);
        voucherOrder.setVoucherId(voucherId);
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
        save(voucherOrder);
        //6.返回订单ID
        return Result.ok(order);
    }
}
