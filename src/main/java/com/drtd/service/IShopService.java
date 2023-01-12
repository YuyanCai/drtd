package com.drtd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.drtd.dto.Result;
import com.drtd.entity.Shop;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
