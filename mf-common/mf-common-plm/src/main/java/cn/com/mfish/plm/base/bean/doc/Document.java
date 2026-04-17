package cn.com.mfish.plm.base.bean.doc;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFObject;

/**
 * 文档实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document")
public class Document extends WFObject {
    private String version;
    private String state;
}