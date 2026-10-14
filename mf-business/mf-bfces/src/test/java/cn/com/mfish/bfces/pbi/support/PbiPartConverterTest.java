package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @description: 产品型号转换器单元测试，覆盖固定字段提取、扩展属性归集、
 * 图片地址拼接与非法编号防护、下拉选项文本转换、信息页地址在缺少凭据时的降级。
 * 不模拟登录上下文（AuthInfoUtils 在无请求时返回null），信息页成功路径由集成环境保障。
 * @author: mfish
 * @date: 2026-10-14
 */
class PbiPartConverterTest {

    private PbiProperties properties;

    private PbiPartConverter converter;

    @BeforeEach
    void setUp() {
        properties = new PbiProperties();
        properties.setDomain("https://plm13test.bfces.com");
        converter = new PbiPartConverter(properties);
    }

    @Test
    void toPart_固定字段提取_扩展属性归集_图片始终生成() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-001");
        row.put("NAME", "电机");
        row.put("OID", "OR:wt.part.WTPart:123");
        row.put("APPLICATIONID", "app-1");
        row.put("COL_A", "值A");

        PbiPart part = converter.toPart(row);

        assertThat(part.getWtPartNumber()).isEqualTo("P-001");
        assertThat(part.getName()).isEqualTo("电机");
        assertThat(part.getImage())
                .isEqualTo("https://plm13test.bfces.com/Windchill/wtcore/test/part/image/P-001.png");
        // 未配置Basic密码（且无登录账号），信息页链接降级为空串
        assertThat(part.getPageUrl()).isEmpty();
        // 固定字段不进入扩展属性，仅保留配置列
        assertThat(part.getAttrs()).hasSize(1);
        assertThat(part.getAttrs().get("COL_A")).isEqualTo("值A");
    }

    @Test
    void toPart_编号含路径穿越字符_图片地址为空() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "../etc/passwd");
        row.put("NAME", "非法编号件");

        PbiPart part = converter.toPart(row);

        assertThat(part.getImage()).isEmpty();
        assertThat(part.getWtPartNumber()).isEqualTo("../etc/passwd");
    }

    @Test
    void toPart_编号为空_图片地址为空() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", null);
        row.put("NAME", "无编号件");

        PbiPart part = converter.toPart(row);

        assertThat(part.getImage()).isEmpty();
    }

    @Test
    void toPart_域名带尾斜杠_图片地址不出现双斜杠() {
        properties.setDomain("https://plm13test.bfces.com/");
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-002");

        PbiPart part = converter.toPart(row);

        assertThat(part.getImage())
                .isEqualTo("https://plm13test.bfces.com/Windchill/wtcore/test/part/image/P-002.png");
    }

    @Test
    void toOptionLabel_编号名称齐全_返回逗号拼接文本() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-001");
        row.put("NAME", "电机");

        assertThat(converter.toOptionLabel(row)).isEqualTo("P-001,电机");
    }

    @Test
    void toOptionLabel_名称为空_仅返回编号带逗号() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-003");

        assertThat(converter.toOptionLabel(row)).isEqualTo("P-003,");
    }

    @Test
    void toOptionLabel_编号为空的脏数据_返回null() {
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", null);
        row.put("NAME", "无编号件");

        assertThat(converter.toOptionLabel(row)).isNull();
    }
}
