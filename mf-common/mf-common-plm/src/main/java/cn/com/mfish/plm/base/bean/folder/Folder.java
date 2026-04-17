package cn.com.mfish.plm.base.bean.folder;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFObject;

/**
 * 文件夹实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("folder")
public class Folder extends WFObject {
    private String name;
}