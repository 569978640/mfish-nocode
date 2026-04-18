package cn.com.mfish.graph.id;

import java.util.List;

/**
 * VID 生成器接口
 * 统一 PLM 业务 ID → VID 生成规则，保证全局唯一
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface VidGenerator {
    /**
     * 生成 VID
     *
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @return VID
     */
    String generate(String bizType, String bizId);

    /**
     * 批量生成 VID
     *
     * @param bizType 业务类型
     * @param bizIds 业务 ID 列表
     * @return VID 列表
     */
    List<String> generateBatch(String bizType, List<String> bizIds);

    /**
     * 从 VID 提取业务 ID
     *
     * @param vid VID
     * @return 业务 ID
     */
    String extractBizId(String vid);

    /**
     * 从 VID 提取业务类型
     *
     * @param vid VID
     * @return 业务类型
     */
    String extractBizType(String vid);

    /**
     * 校验 VID 格式
     *
     * @param vid VID
     * @return true=合法
     */
    boolean validate(String vid);
}