package cn.com.mfish.common.ai.memory;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 文档块（Document Chunk）
 * <p>
 * 由 FileParseService 解析文件后产出的最小内容单元。一个文件可能产生多个 Chunk，
 * 也可能只有一个 Chunk（小文件）。Memory 模块按 Chunk 维度管理文档上下文，
 * 便于后续扩展：按相关性检索（RAG）、按 token 上限分片注入等。
 * </p>
 * <p>
 * 设计为不可变值对象：构造后内容不再修改，需要更新时替换整个 Chunk。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
@Data
@Accessors(chain = true)
public class DocumentChunk {
    /**
     * 块ID（建议使用 UUID 或 fileKey + 序号）
     */
    private String chunkId;
    /**
     * 来源文件的 fileKey（Storage 服务的唯一标识）
     */
    private String fileKey;
    /**
     * 来源文件名（用于 LLM 提示词展示）
     */
    private String fileName;
    /**
     * 文件 MIME 类型（用于 LLM 提示词展示和后续分流处理）
     */
    private String fileType;
    /**
     * 块文本内容（已提取为纯文本，可直接注入 LLM 上下文）
     */
    private String content;
    /**
     * 在原文件中的序号（从 0 开始；单文件单块时为 0）
     */
    private int chunkIndex;
    /**
     * 块字符数（便于 token 估算和上下文裁剪）
     */
    private int length;
    /**
     * 文本向量（embedding），用于向量库相似度检索
     * <p>
     * 维度由 embedding model 决定（如 OpenAI text-embedding-3-small 为 1536 维）。
     * </p>
     * <p>
     * <b>使用约定</b>：
     * <ul>
     *   <li>内存版 Memory：不使用此字段（始终为 null），searchDocuments 走全量返回</li>
     *   <li>向量库版 Memory：由 Store 在写入时调用 embedding model 计算并存储，
     *       chunk 对象上的此字段可为 null（向量库内部维护真正的向量索引）</li>
     *   <li>调试场景：可手动填充此字段用于离线分析</li>
     * </ul>
     * </p>
     * <p>
     * 类型为 {@code float[]} 而非 {@code List<Float>}，避免装箱开销；
     * Lombok {@code @Data} 会基于内容生成 equals/hashCode，正常使用无影响。
     * </p>
     */
    private float[] embedding;
    /**
     * 解析时间戳（毫秒）
     */
    private long parsedAt;

    public DocumentChunk() {
        this.parsedAt = System.currentTimeMillis();
    }
}
