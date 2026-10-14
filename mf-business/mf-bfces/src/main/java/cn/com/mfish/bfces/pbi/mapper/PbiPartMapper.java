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
}
