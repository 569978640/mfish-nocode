package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @description: 视图查询列选择器单元测试，覆盖白名单存在性校验、非法字符过滤、
 * 忽略大小写去重、数据字典异常/为空降级与列信息缓存。
 * @author: mfish
 * @date: 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
class PbiViewColumnSelectorTest {

    @Mock
    private PbiPartMapper pbiPartMapper;

    private PbiViewColumnSelector selector;

    @BeforeEach
    void setUp() {
        selector = new PbiViewColumnSelector(pbiPartMapper);
    }

    @Test
    void buildQueryColumns_非法列不存在列与重复列被剔除() {
        when(pbiPartMapper.getViewColumns())
                .thenReturn(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "COL_A"));

        List<String> columns = selector.buildQueryColumns(
                List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID",
                        "COL_A", "COL_B", "bad col;", "oid", "  "));

        // COL_B视图不存在、"bad col;"含非法字符、oid与OID重复、空白列：均被剔除，顺序保持入参
        assertThat(columns).containsExactly("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "COL_A");
    }

    @Test
    void buildQueryColumns_视图仅有无关列_候选全部被过滤返回空() {
        when(pbiPartMapper.getViewColumns()).thenReturn(List.of("OTHER"));

        List<String> columns = selector.buildQueryColumns(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID"));

        assertThat(columns).isEmpty();
    }

    @Test
    void buildQueryColumns_数据字典查询异常_降级为不做存在性校验() {
        when(pbiPartMapper.getViewColumns()).thenThrow(new RuntimeException("字典表不可用"));

        List<String> columns = selector.buildQueryColumns(List.of("COL_X"));

        // 降级模式下列保留；非法字符与去重校验仍然生效
        assertThat(columns).containsExactly("COL_X");
    }

    @Test
    void buildQueryColumns_数据字典返回空_降级为不做存在性校验() {
        when(pbiPartMapper.getViewColumns()).thenReturn(List.of());

        List<String> columns = selector.buildQueryColumns(List.of("COL_X", "COL_Y"));

        assertThat(columns).containsExactly("COL_X", "COL_Y");
    }

    @Test
    void buildQueryColumns_视图列信息只加载一次并缓存复用() {
        when(pbiPartMapper.getViewColumns())
                .thenReturn(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID"));

        selector.buildQueryColumns(List.of("OID"));
        selector.buildQueryColumns(List.of("WTPARTNUMBER"));

        // 两次组装查询列只查询一次数据字典
        verify(pbiPartMapper, times(1)).getViewColumns();
    }
}
