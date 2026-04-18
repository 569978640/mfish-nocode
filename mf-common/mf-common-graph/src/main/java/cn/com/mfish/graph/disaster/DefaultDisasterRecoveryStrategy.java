package cn.com.mfish.graph.disaster;

import cn.com.mfish.graph.pool.AddressManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认灾备策略实现
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class DefaultDisasterRecoveryStrategy implements DisasterRecoveryStrategy {
    private final AddressManager addressManager;
    private DisasterStatus status = DisasterStatus.NORMAL;
    private String primaryAddress;
    private String backupAddress;

    public DefaultDisasterRecoveryStrategy(AddressManager addressManager, String primaryAddress, String backupAddress) {
        this.addressManager = addressManager;
        this.primaryAddress = primaryAddress;
        this.backupAddress = backupAddress;
    }

    @Override
    public boolean switchover(String reason) {
        log.warn("触发灾备切换: reason={}, from={}, to={}", reason, primaryAddress, backupAddress);
        status = DisasterStatus.SWITCHING;

        try {
            addressManager.markUnavailable(primaryAddress);
            addressManager.markAvailable(backupAddress);

            String temp = primaryAddress;
            primaryAddress = backupAddress;
            backupAddress = temp;

            status = DisasterStatus.FAILOVER;
            log.info("灾备切换完成");
            return true;
        } catch (Exception e) {
            log.error("灾备切换失败", e);
            status = DisasterStatus.NORMAL;
            return false;
        }
    }

    @Override
    public boolean recover() {
        log.info("开始故障恢复: primary={}, backup={}", primaryAddress, backupAddress);
        status = DisasterStatus.RECOVERING;

        try {
            if (backupAddress != null && !addressManager.isAvailable(backupAddress)) {
                addressManager.markAvailable(backupAddress);
            }

            if (primaryAddress != null && !addressManager.isAvailable(primaryAddress)) {
                addressManager.markAvailable(primaryAddress);
            }

            status = DisasterStatus.NORMAL;
            log.info("故障恢复完成");
            return true;
        } catch (Exception e) {
            log.error("故障恢复失败", e);
            status = DisasterStatus.FAILOVER;
            return false;
        }
    }

    @Override
    public DisasterStatus getStatus() {
        return status;
    }
}