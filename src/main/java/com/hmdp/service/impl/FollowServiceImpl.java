package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户
        Long id = UserHolder.getUser().getId();
        //1、判断关注还是取关
        if(isFollow){
            //2、关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

        }else{
            //3、取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id",id)
                    .eq("follow_user_id",followUserId));
            return Result.ok(isSuccess);
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取当前用户
        Long id = UserHolder.getUser().getId();
        //查询是否关注
        Integer count = query().eq("user_id", UserHolder.getUser().getId()).count();
        //判断
        return Result.ok(count>0);
    }
}
