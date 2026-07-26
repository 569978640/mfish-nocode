package cn.com.mfish.common.ai.capability;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill 文件加载器
 * <p>
 * 扫描 classpath:skills/*.md（内置 Skill，打包在 jar 中）和外部目录（用户自定义 Skill），
 * 解析 YAML frontmatter + prompt 正文，构建 {@link SkillInfo} 列表。
 * </p>
 * <p>
 * <b>skill.md 格式</b>：
 * <pre>
 * ---
 * name: translator
 * description: 将文本翻译为指定语言
 * model: deepseek-v3
 * ---
 *
 * 你是一个专业翻译。将以下文本翻译为 {lang}：
 *
 * {text}
 * </pre>
 * <ul>
 *   <li>frontmatter 为简单 {@code key: value} 格式（每行一个），支持字段：name/description/model/title</li>
 *   <li>正文为 prompt 模板，支持 {param} 变量占位符</li>
 *   <li>参数从 {param} 占位符自动提取，无需声明</li>
 *   <li>生成的 inputSchema 中所有参数均为 string 类型</li>
 * </ul>
 * </p>
 * <p>
 * <b>外部目录优先级</b>：
 * <ol>
 *   <li>系统属性 {@code mf.ai.skill.dir}</li>
 *   <li>环境变量 {@code MF_AI_SKILL_DIR}</li>
 *   <li>默认 {@code ${user.dir}/skills/}</li>
 * </ol>
 * 外部目录中的同名 Skill 会覆盖 classpath 内置 Skill。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Slf4j
public class SkillFileLoader {

    /**
     * classpath 扫描模式：jar 内置 Skill
     */
    private static final String CLASSPATH_PATTERN = "classpath*:skills/*.md";

    /**
     * frontmatter 分隔符
     */
    private static final String FRONTMATTER_SEP = "---";

    /**
     * {param} 占位符正则：匹配 {word} 形式的变量名（字母数字下划线）
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

    /**
     * 外部 Skill 目录（可为 null，表示不加载外部 Skill）
     */
    private final String externalDir;

    /**
     * 默认构造：自动解析外部目录（按优先级查找系统属性/环境变量/默认路径）
     */
    public SkillFileLoader() {
        this(resolveExternalDir());
    }

    /**
     * 指定外部目录构造（测试用）
     *
     * @param externalDir 外部 Skill 目录路径，null 表示不加载外部 Skill
     */
    public SkillFileLoader(String externalDir) {
        this.externalDir = externalDir;
    }

    /**
     * 加载所有 Skill：先 classpath（内置），再外部目录（覆盖同名）
     *
     * @return Skill 列表（按加载顺序，外部覆盖内置同名）
     */
    public List<SkillInfo> loadAll() {
        // 用 LinkedHashMap 保持顺序且允许后加载的同名覆盖前一个
        Map<String, SkillInfo> skills = new LinkedHashMap<>();

        int classpathCount = loadFromClasspath(skills);
        int externalCount = loadFromExternalDir(skills);

        log.info("[SkillFileLoader] 加载完成：classpath={} 外部={} 合计={}",
                classpathCount, externalCount, skills.size());
        return new ArrayList<>(skills.values());
    }

    /**
     * 扫描 classpath:skills/*.md（jar 内置 Skill）
     */
    private int loadFromClasspath(Map<String, SkillInfo> skills) {
        int count = 0;
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_PATTERN);
            for (Resource res : resources) {
                String filename = res.getFilename();
                if (filename == null) continue;
                try (InputStream is = res.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    SkillInfo info = parseMarkdown(content, "classpath:skills/" + filename);
                    if (info != null) {
                        skills.put(info.getSkillCode(), info);
                        count++;
                        log.info("[SkillFileLoader] 加载内置 Skill: {} ({})", info.getSkillCode(), info.getSource());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SkillFileLoader] 扫描 classpath:skills/ 失败（可能目录不存在）: {}", e.getMessage());
        }
        return count;
    }

    /**
     * 扫描外部目录 *.md（用户自定义 Skill，覆盖内置同名）
     */
    private int loadFromExternalDir(Map<String, SkillInfo> skills) {
        if (externalDir == null || externalDir.isEmpty()) {
            return 0;
        }
        File dir = new File(externalDir);
        if (!dir.isDirectory()) {
            log.debug("[SkillFileLoader] 外部 Skill 目录不存在: {}", externalDir);
            return 0;
        }
        File[] mdFiles = dir.listFiles((d, name) -> name.endsWith(".md"));
        if (mdFiles == null || mdFiles.length == 0) {
            return 0;
        }
        int count = 0;
        for (File file : mdFiles) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                SkillInfo info = parseMarkdown(content, file.getAbsolutePath());
                if (info != null) {
                    boolean overwrite = skills.containsKey(info.getSkillCode());
                    skills.put(info.getSkillCode(), info);
                    count++;
                    log.info("[SkillFileLoader] 加载外部 Skill: {} ({}){}",
                            info.getSkillCode(), info.getSource(),
                            overwrite ? " [覆盖内置同名]" : "");
                }
            } catch (Exception e) {
                log.warn("[SkillFileLoader] 解析外部 Skill 文件失败 {}: {}", file, e.getMessage());
            }
        }
        return count;
    }

    /**
     * 解析 skill.md 内容：分离 frontmatter 和 prompt 正文，提取参数占位符
     *
     * @param content 文件全文
     * @param source  源路径（日志定位用）
     * @return SkillInfo，解析失败返回 null
     */
    SkillInfo parseMarkdown(String content, String source) {
        if (content == null || content.isBlank()) {
            log.warn("[SkillFileLoader] Skill 文件内容为空: {}", source);
            return null;
        }
        String trimmed = content.trim();

        // 检查 frontmatter 起始 ---
        if (!trimmed.startsWith(FRONTMATTER_SEP)) {
            log.warn("[SkillFileLoader] Skill 文件缺少 frontmatter 起始符 ---: {}", source);
            return null;
        }

        // 查找 frontmatter 结束 ---
        // 跳过开头的 ---，从其后查找下一个独立行的 ---
        int afterFirstSep = trimmed.indexOf('\n') + 1;
        int secondSepIdx = findFrontmatterEnd(trimmed, afterFirstSep);
        if (secondSepIdx < 0) {
            log.warn("[SkillFileLoader] Skill 文件 frontmatter 未闭合（缺少结束 ---）: {}", source);
            return null;
        }

        String frontmatter = trimmed.substring(afterFirstSep, secondSepIdx).trim();
        String promptBody = trimmed.substring(secondSepIdx + FRONTMATTER_SEP.length()).trim();

        // 解析 frontmatter（简单 key: value 格式）
        Map<String, String> meta = parseFrontmatter(frontmatter);

        String skillCode = meta.get("name");
        if (skillCode == null || skillCode.isEmpty()) {
            log.warn("[SkillFileLoader] Skill 文件缺少 name 字段: {}", source);
            return null;
        }

        // 提取 {param} 占位符作为参数列表
        List<String> params = extractParams(promptBody);

        // 基于 params 生成 inputSchema
        String inputSchema = generateInputSchema(params);

        return new SkillInfo()
                .setSkillCode(skillCode)
                .setSkillName(meta.getOrDefault("title", skillCode))
                .setDescription(meta.getOrDefault("description", skillCode))
                .setType(meta.getOrDefault("type", "prompt"))
                .setRequires(parseRequires(meta.get("requires")))
                .setPromptTemplate(promptBody)
                .setModelName(meta.get("model"))
                .setParams(params)
                .setInputSchema(inputSchema)
                .setSource(source);
    }

    /**
     * 查找 frontmatter 结束分隔符 --- 的位置（独立行）
     */
    private int findFrontmatterEnd(String text, int fromIndex) {
        int idx = fromIndex;
        while (idx < text.length()) {
            int lineStart = idx;
            int lineEnd = text.indexOf('\n', idx);
            String line = lineEnd < 0
                    ? text.substring(lineStart)
                    : text.substring(lineStart, lineEnd);
            if (line.trim().equals(FRONTMATTER_SEP)) {
                return lineStart;
            }
            if (lineEnd < 0) break;
            idx = lineEnd + 1;
        }
        return -1;
    }

    /**
     * 解析简单 frontmatter（每行 key: value 格式，不依赖 YAML 库）
     */
    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) continue;
            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();
            // 去除可能的引号包裹
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            meta.put(key, value);
        }
        return meta;
    }

    /**
     * 解析 frontmatter 的 requires 字段为服务ID列表
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>逗号分隔字符串：{@code mf-demo,mf-sys}</li>
     *   <li>YAML 数组格式：{@code [mf-demo,mf-sys]}</li>
     * </ul>
     * </p>
     */
    private List<String> parseRequires(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        // 去除方括号
        String cleaned = value.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 从 prompt 模板提取 {param} 占位符参数名（去重、保序）
     */
    private List<String> extractParams(String template) {
        Set<String> params = new LinkedHashSet<>();
        Matcher m = PARAM_PATTERN.matcher(template);
        while (m.find()) {
            params.add(m.group(1));
        }
        return new ArrayList<>(params);
    }

    /**
     * 基于参数名列表生成 JSON Schema（所有参数为 string 类型，全部 required）
     */
    private String generateInputSchema(List<String> params) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        for (String param : params) {
            JSONObject prop = new JSONObject();
            prop.put("type", "string");
            properties.put(param, prop);
        }
        schema.put("properties", properties);
        if (!params.isEmpty()) {
            schema.put("required", params);
        }
        return schema.toJSONString();
    }

    /**
     * 解析外部 Skill 目录（按优先级：系统属性 > 环境变量 > 默认路径）
     */
    private static String resolveExternalDir() {
        // 1. 系统属性
        String dir = System.getProperty("mf.ai.skill.dir");
        if (dir != null && !dir.isEmpty()) {
            return dir;
        }
        // 2. 环境变量
        dir = System.getenv("MF_AI_SKILL_DIR");
        if (dir != null && !dir.isEmpty()) {
            return dir;
        }
        // 3. 默认 ${user.dir}/skills
        return System.getProperty("user.dir") + File.separator + "skills";
    }
}
