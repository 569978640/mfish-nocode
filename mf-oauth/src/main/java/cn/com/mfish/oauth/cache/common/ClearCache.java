package cn.com.mfish.oauth.cache.common;

import cn.com.mfish.common.oauth.api.vo.TenantVo;
import cn.com.mfish.common.oauth.entity.SsoUser;
import cn.com.mfish.common.redis.common.RedisPrefix;
import cn.com.mfish.oauth.cache.temp.Account2IdTempCache;
import cn.com.mfish.oauth.cache.temp.UserPermissionTempCache;
import cn.com.mfish.oauth.cache.temp.UserRoleTempCache;
import cn.com.mfish.oauth.cache.temp.UserTempCache;
import cn.com.mfish.oauth.cache.temp.UserTenantTempCache;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @description: 缓存清理
 * @author: mfish
 * @date: 2023/6/26
 */
@Component
public class ClearCache {

    @Resource
    UserTenantTempCache userTenantTempCache;
    @Resource
    UserRoleTempCache userRoleTempCache;
    @Resource
    UserPermissionTempCache userPermissionTempCache;
    @Resource
    UserTempCache userTempCache;
    @Resource
    Account2IdTempCache account2IdTempCache;

    /**
     * 移除用户相关缓存
     *
     * @param userId 用户id
     */
    public void removeUserCache(String userId) {
        userTempCache.removeOneCache(userId);
        userTenantTempCache.removeOneCache(userId);
        removeUserAuthCache(Collections.singletonList(userId));
    }

    /**
     * 移除用户相关缓存（含账号、手机号、邮箱到用户ID的映射缓存）
     * <p>
     * 删除用户时必须同步清理 account2id 映射缓存：该缓存默认保留7天，
     * 若只清理用户详情缓存，同账号被重新创建后会命中已失效的旧用户ID，
     * 导致登录时查询不到用户，报“账号不存在或已被禁用”
     * </p>
     *
     * @param user 被删除或变更登录标识的用户信息
     */
    public void removeUserCache(SsoUser user) {
        if (user == null) {
            return;
        }
        removeUserCache(user.getId());
        // 账号、手机号、邮箱均可能作为登录入口，三者到用户ID的映射缓存都需要清理
        removeAccount2IdCache(user.getAccount());
        removeAccount2IdCache(user.getPhone());
        removeAccount2IdCache(user.getEmail());
    }

    /**
     * 清理单个登录标识（账号/手机号/邮箱）到用户ID的映射缓存
     *
     * @param account 登录标识
     */
    public void removeAccount2IdCache(String account) {
        if (account != null && !account.isEmpty()) {
            account2IdTempCache.removeOneCache(account);
        }
    }

    /**
     * 移除用户角色，权限相关缓存
     *
     * @param userIds 用户id
     */
    public void removeUserAuthCache(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> roleKeys = new ArrayList<>();
        List<String> permissionKeys = new ArrayList<>();
        for (String userId : userIds) {
            List<TenantVo> list = userTenantTempCache.getFromCacheAndDB(userId);
            if (list == null) {
                continue;
            }
            //移除通用界面用户角色，权限缓存
            roleKeys.add(RedisPrefix.buildUser2RolesKey(userId, ""));
            for (TenantVo vo : list) {
                roleKeys.add(RedisPrefix.buildUser2RolesKey(userId, vo.getId()));
                permissionKeys.add(RedisPrefix.buildUser2PermissionsKey(userId, vo.getId()));
            }
        }
        userRoleTempCache.removeMoreCache(roleKeys);
        userPermissionTempCache.removeMoreCache(permissionKeys);
    }

}
