package cn.com.mfish.plm;

import cn.com.mfish.common.cloud.annotation.AutoCloud;
import cn.com.mfish.common.core.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@AutoCloud
public class MfPlmApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(MfPlmApplication.class, args);
        Utils.printServerRun(application);
    }
}