package com.drtd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.drtd.entity.BlogComments;
import com.drtd.mapper.BlogCommentsMapper;
import com.drtd.service.IBlogCommentsService;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
