package com.drtd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.drtd.dto.Result;
import com.drtd.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {

    Result queryByRedis();
}
