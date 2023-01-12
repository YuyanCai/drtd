package com.drtd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.drtd.entity.UserInfo;
import com.drtd.mapper.UserInfoMapper;
import com.drtd.service.IUserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
