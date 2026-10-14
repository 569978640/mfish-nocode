package cn.com.mfish.bfces.pbi.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @description: PBI部件缓存仓储单元测试：聚焦ID索引短缓存的读写语义、空值标记与TTL开关。
 * pipeline批量Hash读写与SCAN清空为Redis胶水逻辑，依赖真实Redis集成测试验证。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PbiPartCacheRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PbiPartCacheRepository repository;

    @BeforeEach
    void setUp() {
        // lenient：ttl=0的用例不会触达opsForValue，严格模式下避免误报冗余stub
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCategoryIds_未缓存返回null_空列表标记返回空集合_正常列表解析() {
        String key = PbiPartCacheRepository.CATEGORY_IDS_PREFIX + "CAT1";
        when(valueOperations.get(key)).thenReturn(null, "", "1,2,3");

        assertNull(repository.getCategoryIds("CAT1"));
        assertEquals(0, repository.getCategoryIds("CAT1").size());
        assertEquals(List.of("1", "2", "3"), repository.getCategoryIds("CAT1"));
    }

    @Test
    void putCategoryIds_ttl大于0写入并带过期时间() {
        repository.putCategoryIds("CAT1", List.of("1", "2"), 30L);

        verify(valueOperations).set(eq(PbiPartCacheRepository.CATEGORY_IDS_PREFIX + "CAT1"),
                eq("1,2"), eq(Duration.ofSeconds(30L)));
    }

    @Test
    void putCategoryIds_ttl为0不写入缓存_等同每次实时查库() {
        repository.putCategoryIds("CAT1", List.of("1"), 0L);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void putCategoryIds_空列表也写入_防缓存穿透() {
        repository.putCategoryIds("CAT_EMPTY", List.of(), 30L);

        verify(valueOperations).set(eq(PbiPartCacheRepository.CATEGORY_IDS_PREFIX + "CAT_EMPTY"),
                eq(""), eq(Duration.ofSeconds(30L)));
    }

    @Test
    void 编号索引_查无标记读写为空串_正常ID透传() {
        String noKey = PbiPartCacheRepository.NUMBER_ID_PREFIX + "P1";
        when(valueOperations.get(noKey)).thenReturn("-", "99");

        // "-"是查无结果标记，读为空串（区别于null=未缓存）
        assertEquals("", repository.getIdByNumber("P1"));
        assertEquals("99", repository.getIdByNumber("P1"));

        repository.putIdByNumber("P1", null, 30L);
        verify(valueOperations).set(eq(noKey), eq("-"), eq(Duration.ofSeconds(30L)));
    }

    @Test
    void putIdByNumber_ttl为0不写入() {
        repository.putIdByNumber("P1", "99", 0L);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }
}
