package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author DDDJ
 **/
public class LoginInterceptor implements HandlerInterceptor {


    @Override //前置拦截器
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/*        //1、获取session
        HttpSession session = request.getSession();
        //2、获取session中的用户
        Object user = session.getAttribute("user");
        //3、判断用户是否存在
        if (user == null){
            //4、不存在，拦截  返回401状态码，未授权
            response.setStatus(401);
            return false;
        }
        //5、存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        //6、放行
        return true;
        */

//        1、判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
//
//        //移除用户
//        UserHolder.removeUser();
//
//    }

}
