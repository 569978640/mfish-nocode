package cn.com.mfish.bfces.pbi.mapper;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiPartSpec;
import cn.com.mfish.bfces.pbi.entity.PbiPreviousMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description: PBI产品家谱Mapper。
 * 家谱按"Redis部件Hash优先、miss回Windchill原始表、编号解析始终实时"的方式读取数据（见 PbiFamilyPartReader）：
 * 1.BFCES_PBI_PART_INDX_VIEW 提供二级分类下的底层部件ID（索引视图实时窄查询）；
 * 2.STRINGVALUE/STRINGDEFINITION 提供"上一代物料编码"（部件编号或BASELINE）及DOC/DPF/SCR_ASC规格软属性，
 *   作为Redis Hash对应字段miss时的回源；
 * 3.WTPART/WTPARTMASTER 提供部件编号、生命周期状态及最新已发布版本的部件ID，
 *   其中"编号→最新版本"始终实时查询，不走缓存，保证家谱连接到最新已发布/已作废版本。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.3.0
 */
@Mapper
public interface PbiFamilyMapper {

    /**
     * 根据二级分类查询底层节点部件ID（走专用索引视图BFCES_PBI_PART_INDX_VIEW，单列窄查询）。
     *
     * @param productClassification 二级分类内部名称（PRODUCTCLASSIFICATION）
     * @return 底层部件ID列表（IDA2A2）
     */
    @Select("SELECT IDA2A2 AS PART_ID FROM BFCES_PBI_PART_INDX_VIEW WHERE PRODUCTCLASSIFICATION = #{productClassification}")
    List<String> findLeafPartIds(@Param("productClassification") String productClassification);

    /**
     * 批量查询部件的"上一代物料编码"属性行。
     *
     * @param partIds 当前层部件ID集合
     * @return IDA3A4(部件ID) + VALUE(上一代部件编号) 行列表
     */
    @Select("""
            <script>
            SELECT IDA3A4 AS PART_ID, VALUE AS PREVIOUS_VALUE
            FROM STRINGVALUE
            WHERE IDA3A6 = (SELECT IDA2A2 FROM STRINGDEFINITION WHERE NAME = 'com.bfces.PreviousMaterialCode')
              AND IDA3A4 IN
              <foreach collection="partIds" item="partId" open="(" separator="," close=")">
                #{partId}
              </foreach>
            </script>
            """)
    List<PbiPreviousMaterial> findPreviousMaterials(@Param("partIds") List<String> partIds);

    /**
     * 按部件ID批量查询部件信息（底层叶节点展示用，直接取部件自身行的状态与编号）
     *
     * @param partIds 部件ID集合
     * @return 部件信息列表（partId/state/wtPartNumber）
     */
    @Select("""
            <script>
            SELECT WP.IDA2A2 AS PART_ID,
                   WP.STATESTATE AS STATE,
                   WTM.WTPARTNUMBER AS WT_PART_NUMBER
            FROM wcadmin.WTPART WP
            JOIN wcadmin.WTPARTMASTER WTM ON WP.IDA3MASTERREFERENCE = WTM.IDA2A2
            WHERE WP.IDA2A2 IN
            <foreach collection="partIds" item="partId" open="(" separator="," close=")">
              #{partId}
            </foreach>
            </script>
            """)
    List<PbiFamilyTreeNode> findPartInfoByIds(@Param("partIds") List<String> partIds);

    /**
     * 按部件编号批量查询每个编号最新版本的已发布/已作废部件（版本号降序、迭代号降序取第一行）。
     * 上一代物料编码存的是编号，需用本查询换回最新部件ID后继续向上递归。
     *
     * @param wtPartNumbers 部件编号集合
     * @return 每个编号最新有效版本的部件信息（partId/state/wtPartNumber）
     */
    @Select("""
            <script>
            SELECT PART_ID, STATE, WT_PART_NUMBER
            FROM (
                SELECT WP.IDA2A2 AS PART_ID,
                       WP.STATESTATE AS STATE,
                       WTM.WTPARTNUMBER AS WT_PART_NUMBER,
                       ROW_NUMBER() OVER (
                           PARTITION BY WP.IDA3MASTERREFERENCE
                           ORDER BY WP.VERSIONIDA2VERSIONINFO DESC,
                                    TO_NUMBER(WP.ITERATIONIDA2ITERATIONINFO DEFAULT -1 ON CONVERSION ERROR) DESC
                       ) AS RN
                FROM wcadmin.WTPART WP
                JOIN wcadmin.WTPARTMASTER WTM ON WP.IDA3MASTERREFERENCE = WTM.IDA2A2
                WHERE WP.IDA3VIEW = '24712'
                  AND WP.VERSIONIDA2VERSIONINFO NOT LIKE '%-%'
                  AND WP.STATECHECKOUTINFO = 'c/i'
                  AND WP.STATESTATE IN ('RELEASED', 'OBSOLETE')
                  AND WTM.WTPARTNUMBER IN
                  <foreach collection="wtPartNumbers" item="wtPartNumber" open="(" separator="," close=")">
                    #{wtPartNumber}
                  </foreach>
            )
            WHERE RN = 1
            </script>
            """)
    List<PbiFamilyTreeNode> findLatestPartByNumbers(@Param("wtPartNumbers") List<String> wtPartNumbers);

    /**
     * 按部件ID批量查询规格软属性值（DOC/DPF/SCR_ASC）。
     * 属性值取自 STRINGVALUE.VALUE2，属性定义经 STRINGDEFINITION.NAME 限定，
     * 一次查询取回多个属性定义下所有部件的取值，由Service层按 partId+attrName 归并。
     *
     * @param partIds   部件ID集合（STRINGVALUE.IDA3A4 = WTPART.IDA2A2）
     * @param attrNames 属性内部名集合（如 com.bfces.DOC/com.bfces.DPF/com.bfces.SCR_ASC）
     * @return 规格属性行列表（partId/attrName/value）
     */
    @Select("""
            <script>
            SELECT SV.IDA3A4 AS PART_ID,
                   SD.NAME AS ATTR_NAME,
                   SV.VALUE2 AS SPEC_VALUE
            FROM STRINGVALUE SV
            JOIN STRINGDEFINITION SD ON SV.IDA3A6 = SD.IDA2A2
            WHERE SD.NAME IN
            <foreach collection="attrNames" item="attrName" open="(" separator="," close=")">
              #{attrName}
            </foreach>
              AND SV.IDA3A4 IN
            <foreach collection="partIds" item="partId" open="(" separator="," close=")">
              #{partId}
            </foreach>
            </script>
            """)
    List<PbiPartSpec> findSpecValues(@Param("partIds") List<String> partIds,
                                     @Param("attrNames") List<String> attrNames);
}
