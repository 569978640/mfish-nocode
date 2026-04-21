package cn.com.mfish.graph;

import cn.com.mfish.common.cloud.annotation.AutoCloud;
import cn.com.mfish.common.core.utils.Utils;
import cn.com.mfish.common.log.aspect.LogAspect;
import cn.com.mfish.common.log.service.AsyncSaveLog;
import cn.com.mfish.common.log.service.impl.SysLogServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author: mfish
 * @description: 图数据库中心启动类
 * @date: 2026-04-16
 */

@Slf4j
@AutoCloud
@MapperScan("cn.com.mfish.graph.sync.**.mapper")
public class MfGraphApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(MfGraphApplication.class, args);
        Utils.printServerRun(application);
    }
}