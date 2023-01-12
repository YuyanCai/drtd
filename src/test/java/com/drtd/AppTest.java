package com.drtd; /**
 * @author: xiaocai
 * @since: 2023/01/02/22:01
 */

import com.drtd.entity.Shop;
import com.drtd.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.drtd.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class AppTest {
    @Autowired
    public StringRedisTemplate stringRedisTemplate;

    @Autowired
    public ShopServiceImpl shopService;

    @Test
    public void test1() {
        List<Shop> list = shopService.list();
        System.out.println("sss");
    }

    @Test
    public void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        //返回此映射中包含的映射的 Set 视图。
        //注意：Set 视图意思是 HashMap 中所有的键值对都被看作是一个 set 集合。
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }


    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
//                发送到redis
                stringRedisTemplate.opsForHyperLogLog().add("hll1", values);
            }
        }
        //统计数量
        Long hll1 = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("UV的数量为" + hll1);
    }
}
