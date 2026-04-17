package cn.com.mfish.plm.base.bean.doc;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFObject;

/**
 * 文档主数据实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_master")
public class DocumentMaster extends WFObject {
    private String number;
    private String name;
}