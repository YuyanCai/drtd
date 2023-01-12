package com.drtd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.drtd.dto.Result;
import com.drtd.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
