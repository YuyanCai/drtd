package com.drtd.utils;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.drtd.dto.UserDTO;
import com.drtd.utils.RedisConstants;
import com.drtd.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.drtd.utils.RedisConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    //1. 这里不能做依赖注入,因为LoginInterceptor不属于Spring管理
    private StringRedisTemplate stringRedisTemplate;

    //1.1 那不通过Spring怎么注入呢?我们可以通过该类的构造方法,LoginInterceptor初始化的时候StringRedisTemplate也会被初始化
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取session
//        HttpSession session = request.getSession();
        //1.1 获取token
        String token = request.getHeader("authorization");

        //2.获取session中的用户
//        Object user = session.getAttribute("user");

        //2.2 基于token获取redis中用户
        if (StrUtil.isBlank(token)){
            //4.token不存在,拦截
            response.setStatus(401);
            return false;
        }
        //2.3 token存在的话,获取用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //3.判断用户是否存在
//        if (user == null){
//            //4.不存在,拦截
//            response.setStatus(401);
//            return false;
//        }
        //3.1 判断用户是否存在
        if (userMap.isEmpty()){
            //4.token不存在,拦截
            response.setStatus(401);
            return false;
        }
        //5.存在,保持到TheadLocal
//        UserHolder.saveUser((UserDTO) user);

        //5.1 将查询到的Hash数据转为UserDTO对象(因为我们存到redis中的数据类型是Hash,用户数据为一个Map)

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //5.2 存在,保持用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        //6.1 刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.DAYS);

        //6.放行
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
