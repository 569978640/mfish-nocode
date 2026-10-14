package cn.com.mfish.bfces.pbi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @description: PBI产品型号Mapper
 * @author: mfish
 * @date: 2026-10-12
 * @version: V1.0.0
 */
@Mapper
public interface PbiPartMapper {
    /**
     * 根据产品分类查询产品型号
     * @param productClassification 产品分类
     * @param columns 需要查询的列名
     * @return 返回产品型号列表
     */
    @Select("SELECT ${columns} FROM BFCES_PBI_PART_VIEW WHERE ProductClassification = #{productClassification}")
    List<Map<String, Object>> getPartsByClassification(@Param("productClassification") String productClassification, @Param("columns") String columns);

    /**
     * 根据部件编号查询单个产品型号（产品家谱点击部件编号进入详情页使用；部件编号全库唯一）
     * @param wtPartNumber 部件编号
     * @param columns 需要查询的列名
     * @return 返回产品型号行列表（正常0或1行）
     */
    @Select("SELECT ${columns} FROM BFCES_PBI_PART_VIEW WHERE WTPARTNUMBER = #{wtPartNumber}")
    List<Map<String, Object>> getPartByNumber(@Param("wtPartNumber") String wtPartNumber, @Param("columns") String columns);

    /**
     * 查询PBI零件视图的所有真实列名（来自Oracle数据字典，返回列名为大写）
     * @return 视图中存在的列名列表
     */
    @Select("SELECT DISTINCT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE TABLE_NAME = 'BFCES_PBI_PART_VIEW'")
    List<String> getViewColumns();

    /**
     * 实时查询二级分类下的全部部件ID（仅IDA2A2单列窄查询，走专用索引视图BFCES_PBI_PART_INDX_VIEW）。
     * 缓存模式下作为"新增部件"的实时感知入口，不投影宽列，执行代价远低于特性参数宽行查询。
     * @param productClassification 产品分类
     * @return 部件ID列表（按视图返回顺序）
     */
    @Select("SELECT IDA2A2 FROM BFCES_PBI_PART_INDX_VIEW WHERE PRODUCTCLASSIFICATION = #{productClassification}")
    List<String> findIdsByClassification(@Param("productClassification") String productClassification);

    /**
     * 实时按部件编号查询部件ID（走专用索引视图BFCES_PBI_PART_INDX_VIEW；部件编号全库唯一，正常0或1行；详情接口的实时入口）
     * @param wtPartNumber 部件编号
     * @return 部件ID列表
     */
    @Select("SELECT IDA2A2 FROM BFCES_PBI_PART_INDX_VIEW WHERE WTPARTNUMBER = #{wtPartNumber}")
    List<String> findIdsByNumber(@Param("wtPartNumber") String wtPartNumber);

    /**
     * 按部件ID批量查询全列（缓存未命中的新部件回源用）。
     * 显式使用SELECT *：视图未来新增属性列时自动带出并写入缓存，无需改动SQL与代码。
     * @param partIds 部件ID集合
     * @return 全列行列表（key为大写列名）
     */
    @Select("""
            <script>
            SELECT * FROM BFCES_PBI_PART_VIEW WHERE IDA2A2 IN
            <foreach collection="partIds" item="partId" open="(" separator="," close=")">
              #{partId}
            </foreach>
            </script>
            """)
    List<Map<String, Object>> findAllByIds(@Param("partIds") List<String> partIds);

    /**
     * 按部件ID批量查询指定列（已缓存部件补齐"视图新增列"用，列名必须经视图白名单校验后拼入）。
     * IDA2A2固定作为首列返回，供调用方将列值关联回部件。
     * @param partIds 部件ID集合
     * @param columns 需要补齐的列名（大写、逗号分隔）
     * @return 行列表（key为大写列名）
     */
    @Select("""
            <script>
            SELECT IDA2A2, ${columns} FROM BFCES_PBI_PART_VIEW WHERE IDA2A2 IN
            <foreach collection="partIds" item="partId" open="(" separator="," close=")">
              #{partId}
            </foreach>
            </script>
            """)
    List<Map<String, Object>> findColumnsByIds(@Param("partIds") List<String> partIds, @Param("columns") String columns);

    /**
     * 下拉选项实时窄查询：仅编号、名称两列，不走缓存，保证新部件即时可见。
     * 编号、名称均已在专用索引视图BFCES_PBI_PART_INDX_VIEW中，直接走窄索引视图。
     * @param productClassification 产品分类
     * @return 编号/名称行列表
     */
    @Select("SELECT WTPARTNUMBER, NAME FROM BFCES_PBI_PART_INDX_VIEW WHERE PRODUCTCLASSIFICATION = #{productClassification}")
    List<Map<String, Object>> findOptionsByClassification(@Param("productClassification") String productClassification);
}
