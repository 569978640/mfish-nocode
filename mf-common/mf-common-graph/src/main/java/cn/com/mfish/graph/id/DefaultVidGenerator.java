package cn.com.mfish.graph.id;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 默认 VID 生成器实现
 * 使用 业务类型:业务ID 的格式生成 VID
 *
 * @author mfish
 * @date 2026-04-18
 */
public class DefaultVidGenerator implements VidGenerator {
    private static final String SEPARATOR = ":";
    private static final String HASH_ALGORITHM = "MD5";
    private static final int MAX_VID_LENGTH = 256;

    @Override
    public String generate(String bizType, String bizId) {
        if (bizType == null || bizId == null) {
            throw new IllegalArgumentException("业务类型和业务ID不能为空");
        }
        String rawVid = bizType + SEPARATOR + bizId;
        if (rawVid.length() <= MAX_VID_LENGTH) {
            return rawVid;
        }
        return hashVid(rawVid);
    }

    @Override
    public List<String> generateBatch(String bizType, List<String> bizIds) {
        if (bizIds == null) {
            throw new IllegalArgumentException("业务ID列表不能为空");
        }
        return bizIds.stream().map(id -> generate(bizType, id)).toList();
    }

    @Override
    public String extractBizId(String vid) {
        if (vid == null) {
            return null;
        }
        int index = vid.indexOf(SEPARATOR);
        if (index > 0) {
            return vid.substring(index + 1);
        }
        return vid;
    }

    @Override
    public String extractBizType(String vid) {
        if (vid == null) {
            return null;
        }
        int index = vid.indexOf(SEPARATOR);
        if (index > 0) {
            return vid.substring(0, index);
        }
        return null;
    }

    @Override
    public boolean validate(String vid) {
        if (vid == null || vid.isEmpty()) {
            return false;
        }
        if (vid.length() > MAX_VID_LENGTH) {
            return false;
        }
        return true;
    }

    private String hashVid(String rawVid) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = md.digest(rawVid.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hashed = sb.toString();
            return hashed.substring(0, Math.min(hashed.length(), MAX_VID_LENGTH - 1)) + "h";
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, MAX_VID_LENGTH);
        }
    }
}