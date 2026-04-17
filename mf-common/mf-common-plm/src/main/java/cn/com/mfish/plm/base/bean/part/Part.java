package cn.com.mfish.plm.base.bean.part;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFObject;

/**
 * 部件实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("part")
public class Part extends WFObject {
    private String version;
    private String state;
}