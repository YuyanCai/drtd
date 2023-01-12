package com.drtd.utils;

import cn.hutool.core.lang.UUID;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.ui.context.Theme;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author: xiaocai
 * @since: 2023/01/08/14:42
 */

public class SimpleRedisLock implements ILock {

    //不同的业务有不同的名称
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    //对锁定义一个统一的前缀
    private static final String KEY_PREFIX = "lock:";

    //锁的名称要求用户传递给我们，所以这里我们定义一个构造函数
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        /**
         * 版本一：
         * 基础实现
         * key就是固定前缀+锁的名称，value就是线程标识
         * SET lock thread1 NX EX 10
         */
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        /**
         * 版本一：
         * 释放锁
         */
//        stringRedisTemplate.delete(ID_PREFIX + name);

        /**
         * 版本二：
         * 释放锁的时候判断是不是当前线程的锁
         */
//        //获取线程id
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取key
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }

        /**
         * 版本三
         * 通过lua脚本来释放锁
         */
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
