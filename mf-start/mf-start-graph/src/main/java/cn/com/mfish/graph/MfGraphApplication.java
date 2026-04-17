package cn.com.mfish.graph;

import cn.com.mfish.common.cloud.annotation.AutoCloud;
import cn.com.mfish.common.core.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author: mfish
 * @description: 图数据库中心启动类
 * @date: 2026-04-16
 */

@Slf4j
@AutoCloud
@MapperScan({
    "cn.com.mfish.oauth.mapper",
    "cn.com.mfish.plm.base.mapper"
})
public class MfGraphApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(MfGraphApplication.class, args);
        Utils.printServerRun(application);
    }
}