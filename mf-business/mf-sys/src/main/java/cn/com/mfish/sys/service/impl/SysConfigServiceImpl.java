package cn.com.mfish.sys.service.impl;

import cn.com.mfish.common.core.exception.MyRuntimeException;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.StringUtils;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.sys.entity.SysConfig;
import cn.com.mfish.sys.mapper.SysConfigMapper;
import cn.com.mfish.sys.service.SysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @description: 界面配置
 * @author: mfish
 * @date: 2023-03-07
 * @version: V2.4.1
 */
@Service
@RefreshScope
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
    /**
     * 全局风格配置归属的用户ID（超户），样式配置对所有用户统一生效
     */
    private static final String GLOBAL_STYLE_USER_ID = AuthInfoUtils.SUPER_ACCOUNT_ID;

    /**
     * 配置类型：0 样式风格
     */
    private static final Integer TYPE_STYLE = 0;

    /**
     * 配置类型：1 表格配置（按用户隔离）
     */
    private static final Integer TYPE_TABLE = 1;

    /**
     * 样式配置读取模式：true 全局统一读取管理员配置，false 按用户各自配置
     * 支持在 Nacos 配置中心动态调整，默认全局统一
     */
    @Value("${sys.config.style-global:true}")
    private boolean styleGlobal;

    /**
     * 查询用户系统配置列表
     *
     * @param userId 用户ID
     * @return 系统配置列表
     */
    @Override
    public List<SysConfig> querySysConfig(String userId) {
        if (StringUtils.isEmpty(userId)) {
            throw new MyRuntimeException("错误：用户ID或类型不允许为空");
        }
        List<SysConfig> configs = new ArrayList<>();
        if (styleGlobal) {
            // 全局统一：所有用户都读取超户名下的同一条样式配置
            SysConfig styleConfig = queryConfig(GLOBAL_STYLE_USER_ID, TYPE_STYLE);
            if (styleConfig != null) {
                configs.add(styleConfig);
            }
            // 表格配置仍按用户隔离
            configs.addAll(baseMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getUserId, userId)
                    .eq(SysConfig::getType, TYPE_TABLE)));
        } else {
            // 按用户各自配置：读取当前用户的全部配置
            configs.addAll(baseMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getUserId, userId)));
        }
        return configs;
    }

    /**
     * 根据用户ID和配置类型查询系统配置
     *
     * @param userId 用户ID
     * @param type   配置类型
     * @return 系统配置对象
     */
    @Override
    public SysConfig querySysConfig(String userId, Integer type) {
        if (StringUtils.isEmpty(userId) || null == type) {
            throw new MyRuntimeException("错误：用户ID或类型不允许为空");
        }
        // 全局统一模式下，样式配置统一按超户ID查询
        String queryUserId = styleGlobal && Objects.equals(TYPE_STYLE, type) ? GLOBAL_STYLE_USER_ID : userId;
        return queryConfig(queryUserId, type);
    }

    /**
     * 保存系统配置（已存在则更新，否则新增）
     *
     * @param sysConfig 系统配置对象
     * @return 保存结果
     */
    @Override
    public Result<SysConfig> saveSysConfig(SysConfig sysConfig) {
        String userId = AuthInfoUtils.getCurrentUserId();
        if (StringUtils.isEmpty(userId)) {
            throw new MyRuntimeException("错误：未获取到当前用户信息");
        }
        // 全局统一模式下，样式配置仅管理员可修改，且统一保存到超户名下，保证所有用户生效
        if (styleGlobal && Objects.equals(TYPE_STYLE, sysConfig.getType())) {
            if (!AuthInfoUtils.isSuper(userId) && !"admin".equals(AuthInfoUtils.getCurrentAccount())) {
                return Result.ok(sysConfig, "提示:界面配置-全局界面风格仅管理员可修改!");
            }
            userId = GLOBAL_STYLE_USER_ID;
        }
        SysConfig config = queryConfig(userId, sysConfig.getType());
        if (config != null) {
            config.setConfig(sysConfig.getConfig());
            if (baseMapper.updateById(config) == 1) {
                return Result.ok(sysConfig, "界面配置-编辑成功!");
            }
            return Result.fail(sysConfig, "错误:界面配置-编辑失败!");
        }
        sysConfig.setUserId(userId);
        if (baseMapper.insert(sysConfig) == 1) {
            return Result.ok(sysConfig, "界面配置-新增成功!");
        }
        return Result.fail(sysConfig, "错误:界面配置-新增失败!");
    }

    /**
     * 按用户ID和类型精确查询配置记录
     *
     * @param userId 用户ID
     * @param type   配置类型
     * @return 配置记录
     */
    private SysConfig queryConfig(String userId, Integer type) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getUserId, userId)
                .eq(SysConfig::getType, type));
    }
}
