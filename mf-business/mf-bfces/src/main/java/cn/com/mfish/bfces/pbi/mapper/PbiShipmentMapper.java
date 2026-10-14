package cn.com.mfish.bfces.pbi.mapper;

import cn.com.mfish.bfces.pbi.entity.PbiShipment;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description: 实际发货量Mapper。数据来自接口库（另一台Oracle服务器的 BFCES_INTERFACE 库），
 * 通过 {@code @DS("interface")} 切换到 interface 数据源，与主库 Windchill 查询物理隔离，
 * 未标注该注解的 Mapper 仍默认走 master 数据源。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Mapper
@DS("interface")
public interface PbiShipmentMapper {

    /**
     * 按部件编号批量查询实际发货量（一次 IN 查询取回全部家谱节点的发货量，避免逐个查询的 N+1 问题）。
     *
     * @param items 部件编号集合（WTPARTMASTER.WTPARTNUMBER，对应视图 ITEM 列）
     * @return 发货量行列表（item/shipment），视图中无记录的编号不在列表中
     */
    @Select("""
            <script>
            SELECT ITEM, SHIPMENT
            FROM BFCES_INTERFACE.V_WINDCHILL_SHIPMENT
            WHERE ITEM IN
            <foreach collection="items" item="item" open="(" separator="," close=")">
              #{item}
            </foreach>
            </script>
            """)
    List<PbiShipment> findShipments(@Param("items") List<String> items);
}
