package com.drtd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.drtd.dto.Result;
import com.drtd.entity.Shop;
import com.drtd.entity.ShopType;
import com.drtd.mapper.ShopTypeMapper;
import com.drtd.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.drtd.utils.RedisConstants.*;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopTypeService shopTypeService;

    @Override
    public Result queryByRedis() {
        //1.从redis查询商铺缓存
        String shopTypeList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopTypeList)) {
            //3.存在直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeList, ShopType.class);
            System.out.println("走了redis缓存！");
            return Result.ok(shopTypes);
        }

        //4.不存在,去数据库查询
        List<ShopType> shopTypes = shopTypeService.query().orderByAsc("sort").list();
        if (shopTypes == null) {
            return Result.fail("店铺不存在哦!");
        }

        /**
         * 5.数据库中不存在记录,返回404
         * 数据库存在,将信息写入redis,接着返回结果
         */
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypes),CACHE_SHOP_TYPE_TTL, TimeUnit.DAYS);

        return Result.ok(shopTypes);
    }
}
