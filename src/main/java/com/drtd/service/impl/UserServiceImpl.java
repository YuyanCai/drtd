package com.drtd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.drtd.dto.LoginFormDTO;
import com.drtd.dto.Result;
import com.drtd.dto.UserDTO;
import com.drtd.entity.User;
import com.drtd.mapper.UserMapper;
import com.drtd.service.IUserService;
import com.drtd.utils.RegexUtils;
import com.drtd.utils.UserHolder;
import com.sun.org.apache.regexp.internal.RE;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20190711.SmsClient;
import com.tencentcloudapi.sms.v20190711.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20190711.models.SendSmsResponse;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.drtd.utils.RedisConstants.*;
import static com.drtd.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保持验证码到session
//        session.setAttribute("code", code);

        //4.保持验证码到redis
        //字符串类型的最好都写成常量的形式,这样以后维护的时候比较方便
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
//        腾讯云发送
//        UserServiceImpl.sendMessage(phone, code);

        //工具类随机发送
        //5.发送验证码
        log.debug("发送的验证码为:" + code);
        return Result.ok();
    }

    @Override
    public Result userLogin(LoginFormDTO loginForm, HttpSession session) {
        //1.获取手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3.校验验证码
//        Object cachecode = session.getAttribute("code");

        //3.1校验验证码通过redis
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        //4.检查用户是否存在
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        User user = baseMapper.selectOne(wrapper);
        //4.1判断用户是否存在
        if (user == null) {
            //4.2不存在创建用户
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            baseMapper.insert(user);
        }

        //5.保持用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //5.1 保持用户信息到redis
        //1.随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //2.将user对象转化为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        //这里用了lambda表达式来直接实现这个接口了,意思就是将所有字段都转换为string类型
                        .setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));
//        3.存储
        //3.1这里选择redis的hash数据结构,就是一个key(token),对应着一个hash结构的value(对象属性:对象值)
        //opsForHash().put()是一条一条的存储对象的value,如下格式
//        key: name:li
//        key: age:21

//        opsForHash().putAll是一下把对象数据全放进去,也就是如下这种格式
//        key : name:zs
//              age:22
//              address:beijing
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //3.2 我们要给token设置有效期,因为随着用户越来越多,redis压力是越来越大,所以设置token有效期30分钟
        //LOGIN_USER_TTL为30分钟
        //现在存在的问题是,从登录的那一刻起,30分钟改token就会被踢出,那么30分钟之后还需要重新认证,所以我们要设置只要用户活跃就不踢出token
//        1.我们通过拦截器来判断用户是不是活跃.  2.活跃的话就更新token的过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.DAYS);

//        4.返回token
        return Result.ok(token);
    }


    @Override
    public Result userLogout() {
        return Result.ok();
    }

    @Override
    public Result loginByPasswd(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();

        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }

//        校验密码
        if (StringUtils.isEmpty(password)) {
            return Result.fail("请输入密码");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        User user = userService.getOne(wrapper);
        if (user == null) {
            return Result.fail("该用户不存在，请注册或通过验证登录！");
        }

        if (user.getPhone().equals(phone) && user.getPassword().equals(password)) {
            String token = UUID.randomUUID().toString(true);
            //2.将user对象转化为hash存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            //这里用了lambda表达式来直接实现这个接口了,意思就是将所有字段都转换为string类型
                            .setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
            stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
            return Result.ok(token);
        } else {
            return Result.fail("请检查手机号或密码！");
        }
    }

    @Override
    public Result sign() {
//       1.获取当前用户
        Long userId = UserHolder.getUser().getId();
//       2.获取日期
        LocalDateTime now = LocalDateTime.now();
//       3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
//       4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
//        5.写入redis setbit key offset value
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

//    @Override
//    public Result register(LoginFormDTO loginForm) {
//        String phone = loginForm.getPhone();
//        String password = loginForm.getPassword();
//
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            //2.不符合,返回错误信息
//            return Result.fail("手机号格式错误");
//        }
//
//        if (StringUtils.isEmpty(password)) {
//            return Result.fail("请输入密码");
//        }
//
//        //判断用户存不存在
//        User byId = userService.getById(phone);
//
////        不存在，保存进数据库
//        if (byId != null) {
//            return Result.fail("该用户已存在，请返回登录");
//        }
//
//        User user = new User();
//        user.setPhone(phone);
//        user.setPassword(password);
//
//        QueryWrapper<User> wrapper = new QueryWrapper<>();
//
//        wrapper.eq("phone", phone);
//
//        userService.update(user, wrapper);
//
//        return Result.ok("恭喜您，注册成功！");
//
//
//    }

    //发送短信方法
    public static void sendMessage(String phone, String code) {
        try {
            /* 必要步骤：
             * 实例化一个认证对象，入参需要传入腾讯云账户密钥对secretId，secretKey。
             * 这里采用的是从环境变量读取的方式，需要在环境变量中先设置这两个值。
             * 你也可以直接在代码中写死密钥对，但是小心不要将代码复制、上传或者分享给他人，
             * 以免泄露密钥对危及你的财产安全。
             * SecretId、SecretKey 查询: https://console.cloud.tencent.com/cam/capi */
            Credential cred = new Credential("***", "***");
            // 实例化一个http选项，可选，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            // 设置代理（无需要直接忽略）
            // httpProfile.setProxyHost("真实代理ip");
            // httpProfile.setProxyPort(真实代理端口);
            /* SDK默认使用POST方法。
             * 如果你一定要使用GET方法，可以在这里设置。GET方法无法处理一些较大的请求 */
            httpProfile.setReqMethod("POST");
            /* SDK有默认的超时时间，非必要请不要进行调整
             * 如有需要请在代码中查阅以获取最新地默认值 */
            httpProfile.setConnTimeout(60);
            /* 指定接入地域域名，默认就近地域接入域名为 sms.tencentcloudapi.com ，也支持指定地域域名访问，例如广州地域的域名为 sms.ap-guangzhou.tencentcloudapi.com */
            httpProfile.setEndpoint("sms.tencentcloudapi.com");

            /* 非必要步骤:
             * 实例化一个客户端配置对象，可以指定超时时间等配置 */
            ClientProfile clientProfile = new ClientProfile();
            /* SDK默认用TC3-HMAC-SHA256进行签名
             * 非必要请不要修改这个字段 */
            clientProfile.setSignMethod("HmacSHA256");
            clientProfile.setHttpProfile(httpProfile);
            /* 实例化要请求产品(以sms为例)的client对象
             * 第二个参数是地域信息，可以直接填写字符串ap-guangzhou，支持的地域列表参考 https://cloud.tencent.com/document/api/382/52071#.E5.9C.B0.E5.9F.9F.E5.88.97.E8.A1.A8 */
            SmsClient client = new SmsClient(cred, "ap-guangzhou", clientProfile);
            /* 实例化一个请求对象，根据调用的接口和实际情况，可以进一步设置请求参数
             * 你可以直接查询SDK源码确定接口有哪些属性可以设置
             * 属性可能是基本类型，也可能引用了另一个数据结构
             * 推荐使用IDE进行开发，可以方便地跳转查阅各个接口和数据结构的文档说明 */
            SendSmsRequest req = new SendSmsRequest();

            /* 填充请求参数,这里request对象的成员变量即对应接口的入参
             * 你可以通过官网接口文档或跳转到request对象的定义处查看请求参数的定义
             * 基本类型的设置:
             * 帮助链接：
             * 短信控制台: https://console.cloud.tencent.com/smsv2
             * 腾讯云短信小助手: https://cloud.tencent.com/document/product/382/3773#.E6.8A.80.E6.9C.AF.E4.BA.A4.E6.B5.81 */

            /* 短信应用ID: 短信SdkAppId在 [短信控制台] 添加应用后生成的实际SdkAppId，示例如1400006666 */
            // 应用 ID 可前往 [短信控制台](https://console.cloud.tencent.com/smsv2/app-manage) 查看
            String sdkAppId = "1400672407";
            req.setSmsSdkAppid(sdkAppId);

            /* 短信签名内容: 使用 UTF-8 编码，必须填写已审核通过的签名 */
            // 签名信息可前往 [国内短信](https://console.cloud.tencent.com/smsv2/csms-sign) 或 [国际/港澳台短信](https://console.cloud.tencent.com/smsv2/isms-sign) 的签名管理查看
            String signName = "强哥说Java";
            req.setSign(signName);


            /* 模板 ID: 必须填写已审核通过的模板 ID */
            // 模板 ID 可前往 [国内短信](https://console.cloud.tencent.com/smsv2/csms-template) 或 [国际/港澳台短信](https://console.cloud.tencent.com/smsv2/isms-template) 的正文模板管理查看
            String templateId = "1399701";
            req.setTemplateID(templateId);

            /* 模板参数: 模板参数的个数需要与 TemplateId 对应模板的变量个数保持一致，若无模板参数，则设置为空 */
            String[] templateParamSet = {code};
            req.setTemplateParamSet(templateParamSet);

            /* 下发手机号码，采用 E.164 标准，+[国家或地区码][手机号]
             * 示例如：+8613711112222， 其中前面有一个+号 ，86为国家码，13711112222为手机号，最多不要超过200个手机号 */
            String[] phoneNumberSet = {"+86" + phone};
            req.setPhoneNumberSet(phoneNumberSet);

            /* 用户的 session 内容（无需要可忽略）: 可以携带用户侧 ID 等上下文信息，server 会原样返回 */
            String sessionContext = "";
            req.setSessionContext(sessionContext);

            /* 短信码号扩展号（无需要可忽略）: 默认未开通，如需开通请联系 [腾讯云短信小助手] */
            String extendCode = "";
            req.setExtendCode(extendCode);

            /* 国际/港澳台短信 SenderId（无需要可忽略）: 国内短信填空，默认未开通，如需开通请联系 [腾讯云短信小助手] */
            String senderid = "";
            req.setSenderId(senderid);

            /* 通过 client 对象调用 SendSms 方法发起请求。注意请求方法名与请求对象是对应的
             * 返回的 res 是一个 SendSmsResponse 类的实例，与请求对象对应 */
            SendSmsResponse res = client.SendSms(req);

            // 输出json格式的字符串回包
            System.out.println(SendSmsResponse.toJsonString(res));

        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
        }
    }
}
