package cn.com.mfish.bfces;

import cn.com.mfish.common.cloud.annotation.AutoCloud;
import cn.com.mfish.common.core.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @description: BFCES 服务启动类
 * @author: mfish
 * @date: 2026-09-27
 * @version: V2.4.1
 */
@Slf4j
@AutoCloud
public class MfBfcesApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(MfBfcesApplication.class, args);
        Utils.printServerRun(application);
    }
}