package com.drtd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.drtd.dto.LoginFormDTO;
import com.drtd.dto.Result;
import com.drtd.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result userLogin(LoginFormDTO loginForm, HttpSession session);

    Result userLogout();

//
    Result loginByPasswd(LoginFormDTO loginForm);

    Result sign();

    Result signCount();
//
//    Result register(LoginFormDTO loginForm);
}
