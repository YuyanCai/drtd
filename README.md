# drdp

毕设项目

## 前端文件、sql文件都在resources目录下
前端文件可以直接放到nginx的html目录下，Nginx配置如下：

```
 14     location /api {
 15         default_type  application/json;
 16         #internal;
 17         keepalive_timeout   30s;
 18         keepalive_requests  1000;
 19         #支持keep-alive
 20         proxy_http_version 1.1;
 21         rewrite /api(/.*) $1 break;
 22         proxy_pass_request_headers on;
 23         #more_clear_input_headers Accept-Encoding;
 24         proxy_next_upstream error timeout;
 25         #proxy_pass http://10.216.41.172:8081;
 26         #proxy_pass http://192.168.100.148:8081;
 27         proxy_pass http://backed;
 28     }
 ```
这里的backed是一个负载均衡，也可以注释掉，像我上面写的那样


## redis注意点
涉及优惠卷秒杀，redis中需要提前创建好key：value，不然会一直报错
博客地址：https://blog.csdn.net/qq_45714272/article/details/128632144?spm=1001.2014.3001.5502

## 视频讲解
https://www.bilibili.com/video/BV1JR4y127MD/?spm_id_from=333.999.0.0

 
 
 
