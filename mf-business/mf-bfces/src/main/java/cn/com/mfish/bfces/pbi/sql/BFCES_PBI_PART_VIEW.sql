WITH
-- ===================== 1. 所有属性定义预提取（四种类型统一模式） =====================
STRING_DEFS AS (
    SELECT IDA2A2, NAME
    FROM wcadmin.STRINGDEFINITION
    WHERE NAME IN (
                   'com.bfces.PreviousMaterialCode',
                   'com.bfces.DPF',
                   'com.bfces.SootLoad',
                   'com.bfces.SCR_ASCPGM',
                   'com.bfces.DU_SU',
                   'com.bfces.CESServicePN',
                   'com.bfces.CustomerItem',
                   'com.bfces.EngineMake',
                   'com.bfces.DPFSOR',
                   'com.bfces.NOxIn_Out',
                   'com.bfces.PartUserItemType',
                   'com.bfces.Rating',
                   'com.bfces.RepPartNum',
                   'com.bfces.EngineModel',
                   'com.bfces.TLABP',
                   'com.bfces.DOCPGM',
                   'com.bfces.Mixer',
                   'com.bfces.DOC',
                   'com.bfces.SCR_ASC',
                   'com.bfces.DP_P',
                   'com.bfces.UQS',
                   'com.bfces.Usage',
                   'com.bfces.OSPCustomer',
                   'com.bfces.CustomerInfo',
                   'com.bfces.Config',
                   'com.bfces.PartDEF',
                   'com.bfces.PartParentChildRelationship',
                   'com.bfces.MaterialType',
                   'com.bfces.EmissionStandard',
                   'com.bfces.EGTS',
                   'com.bfces.SCRCanning',
                   'com.bfces.VClamp',
                   'com.bfces.Bracket',
                   'com.bfces.ReleasePhaseCode',
                   'com.bfces.EBUNumeber',
                   'com.bfces.PEOption',
                   'com.bfces.Application',
                   'com.bfces.EngVPIPjtName',
                   'com.bfces.DPFPGM',
                   'com.bfces.DOCCanning',
                   'com.bfces.DPFCanning',
                   'com.bfces.ProductClassification'
        )
),
BOOLEAN_DEFS AS (
    SELECT IDA2A2, NAME
    FROM wcadmin.BOOLEANDEFINITION
    WHERE NAME IN (
        'com.bfces.OSP'
        )
),
UNIT_DEFS AS (
    SELECT IDA2A2, NAME
    FROM wcadmin.UNITDEFINITION
    WHERE NAME IN (
        'com.bfces.Mass'
        )
),
TIMESTAMP_DEFS AS (
    SELECT IDA2A2, NAME
    FROM wcadmin.TIMESTAMPDEFINITION
    WHERE NAME IN (
        'com.bfces.EffectiveData'
        )
),
-- ===================== 2. 业务筛选：合格物料主数据 =====================
QUALIFIED_PART_MASTER AS (
    SELECT DISTINCT w.IDA3MASTERREFERENCE
    FROM wcadmin.WTPART w
             JOIN wcadmin.STRINGVALUE s1
                  ON w.IDA2A2 = s1.IDA3A4
                      AND s1.IDA3A6 = (SELECT IDA2A2 FROM STRING_DEFS WHERE NAME = 'com.bfces.ProductClassification')
                      AND s1.VALUE2 IS NOT NULL
             JOIN wcadmin.STRINGVALUE s2
                  ON w.IDA2A2 = s2.IDA3A4
                      AND s2.IDA3A6 = (SELECT IDA2A2 FROM STRING_DEFS WHERE NAME = 'com.bfces.MaterialType')
                      AND s2.VALUE2 = 'Finished product(SOP)'
    WHERE w.IDA3VIEW = '24712'
      AND w.STATESTATE IN ('RELEASED', 'OBSOLETE')
      AND w.STATECHECKOUTINFO = 'c/i'
),
-- ===================== 3. 最新版本定位：每个主物料取最新版本+最新迭代 =====================
PART_MASTER_ALL AS (
    SELECT IDA3MASTERREFERENCE, MAX_PART_ID
    FROM (
             SELECT
                 WP.IDA3MASTERREFERENCE,
                 WP.IDA2A2 AS MAX_PART_ID,
                 ROW_NUMBER() OVER (
                PARTITION BY WP.IDA3MASTERREFERENCE
                ORDER BY
                    WP.VERSIONIDA2VERSIONINFO DESC,
                    TO_NUMBER(WP.ITERATIONIDA2ITERATIONINFO DEFAULT -1 ON CONVERSION ERROR) DESC
            ) AS RN
             FROM wcadmin.WTPART WP
             WHERE WP.IDA3VIEW = '24712'
               AND WP.VERSIONIDA2VERSIONINFO NOT LIKE '%-%'
               AND WP.STATECHECKOUTINFO = 'c/i'
               AND WP.STATESTATE IN ('RELEASED', 'OBSOLETE')
               AND WP.IDA3MASTERREFERENCE IN (SELECT IDA3MASTERREFERENCE FROM QUALIFIED_PART_MASTER)
         )
    WHERE RN = 1
),
-- ===================== 4. 各类属性行转列聚合 =====================
STRING_ATTR_AGG AS (
    SELECT
        sv.IDA3A4,
        LISTAGG(CASE sd.NAME WHEN 'com.bfces.PreviousMaterialCode' THEN sv.value2 END, ',')
            WITHIN GROUP (ORDER BY sv.IDA2A2) AS PreviousMaterialCode,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DPF' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DPF,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.SootLoad' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS SootLoad,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.SCR_ASCPGM' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS SCR_ASCPGM,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DU_SU' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DU_SU,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.CESServicePN' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS CESServicePN,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.CustomerItem' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS CustomerItem,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EngineMake' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EngineMake,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DPFSOR' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DPFSOR,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.NOxIn_Out' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS NOxIn_Out,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.PartUserItemType' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS PartUserItemType,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Rating' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Rating,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.RepPartNum' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS RepPartNum,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EngineModel' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EngineModel,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.TLABP' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS TLABP,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DOCPGM' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DOCPGM,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Mixer' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Mixer,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DOC' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DOC,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.SCR_ASC' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS SCR_ASC,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DP_P' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DP_P,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.UQS' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS UQS,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Usage' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Usage,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.OSPCustomer' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS OSPCustomer,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.CustomerInfo' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS CustomerInfo,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Config' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Config,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.PartDEF' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS PartDEF,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.PartParentChildRelationship' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS PartParentChildRelationship,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.MaterialType' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS MaterialType,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EmissionStandard' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EmissionStandard,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EGTS' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EGTS,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.SCRCanning' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS SCRCanning,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.VClamp' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS VClamp,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Bracket' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Bracket,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.ReleasePhaseCode' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS ReleasePhaseCode,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EBUNumeber' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EBUNumeber,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.PEOption' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS PEOption,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.Application' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS Application,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.EngVPIPjtName' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS EngVPIPjtName,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DPFPGM' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DPFPGM,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DOCCanning' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DOCCanning,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.DPFCanning' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS DPFCanning,
    LISTAGG(CASE sd.NAME WHEN 'com.bfces.ProductClassification' THEN sv.value2 END, ',')
    WITHIN GROUP (ORDER BY sv.IDA2A2) AS ProductClassification
FROM wcadmin.STRINGVALUE sv
    JOIN STRING_DEFS sd
ON sv.IDA3A6 = sd.IDA2A2
GROUP BY sv.IDA3A4
    ),
    BOOLEAN_ATTR_AGG AS (
SELECT
    sv.IDA3A4,
    MAX(CASE bd.NAME WHEN 'com.bfces.OSP' THEN sv.VALUE END) AS OSP
FROM wcadmin.BOOLEANVALUE sv
    JOIN BOOLEAN_DEFS bd
ON sv.IDA3A6 = bd.IDA2A2
GROUP BY sv.IDA3A4
    ),
    UNIT_ATTR_AGG AS (
SELECT
    sv.IDA3A4,
    MAX(CASE ud.NAME WHEN 'com.bfces.Mass' THEN sv.VALUE END) AS Mass
FROM wcadmin.UNITVALUE sv
    JOIN UNIT_DEFS ud
ON sv.IDA3A6 = ud.IDA2A2
GROUP BY sv.IDA3A4
    ),
    TIMESTAMP_ATTR_AGG AS (
SELECT
    sv.IDA3A4,
    MAX(CASE td.NAME WHEN 'com.bfces.EffectiveData' THEN sv.VALUE END) AS EffectiveData
FROM wcadmin.TIMESTAMPVALUE sv
    JOIN TIMESTAMP_DEFS td
ON sv.IDA3A6 = td.IDA2A2
GROUP BY sv.IDA3A4
    )
SELECT
    wtp.IDA2A2,
    'OR:wt.part.WTPart:' || wtp.IDA2A2 AS oid,
    wpm.WTPARTNUMBER,
    wpm.NAME,
    wtp.STATESTATE,
    wtp.VERSIONIDA2VERSIONINFO,
    wtp.ITERATIONIDA2ITERATIONINFO,
    NVL(sa.PreviousMaterialCode, '') AS PreviousMaterialCode,
    NVL(sa.DPF, '') AS DPF,
    NVL(sa.SootLoad, '') AS SootLoad,
    NVL(sa.SCR_ASCPGM, '') AS SCR_ASCPGM,
    NVL(sa.DU_SU, '') AS DU_SU,
    NVL(sa.CESServicePN, '') AS CESServicePN,
    NVL(sa.CustomerItem, '') AS CustomerItem,
    NVL(sa.EngineMake, '') AS EngineMake,
    NVL(sa.DPFSOR, '') AS DPFSOR,
    NVL(sa.NOxIn_Out, '') AS NOxIn_Out,
    NVL(sa.PartUserItemType, '') AS PartUserItemType,
    NVL(sa.Rating, '') AS Rating,
    NVL(sa.RepPartNum, '') AS RepPartNum,
    NVL(sa.EngineModel, '') AS EngineModel,
    NVL(sa.TLABP, '') AS TLABP,
    NVL(sa.DOCPGM, '') AS DOCPGM,
    NVL(sa.Mixer, '') AS Mixer,
    NVL(sa.DOC, '') AS DOC,
    NVL(sa.SCR_ASC, '') AS SCR_ASC,
    NVL(sa.DP_P, '') AS DP_P,
    NVL(sa.UQS, '') AS UQS,
    CASE ba.OSP WHEN 1 THEN '是' WHEN 0 THEN '否' END AS OSP,
    NVL(ua.Mass, '') AS Mass,
    NVL(sa.Usage, '') AS Usage,
    NVL(sa.OSPCustomer, '') AS OSPCustomer,
    NVL(sa.CustomerInfo, '') AS CustomerInfo,
    NVL(sa.Config, '') AS Config,
    NVL(sa.PartDEF, '') AS PartDEF,
    NVL(sa.PartParentChildRelationship, '') AS PartParentChildRelationship,
    NVL(sa.MaterialType, '') AS MaterialType,
    NVL(sa.EmissionStandard, '') AS EmissionStandard,
    NVL(sa.EGTS, '') AS EGTS,
    NVL(sa.SCRCanning, '') AS SCRCanning,
		NVL(sa.VClamp, '') AS VClamp,
    NVL(sa.Bracket, '') AS Bracket,
    NVL(ta.EffectiveData, '') AS EffectiveData,
    NVL(sa.ReleasePhaseCode, '') AS ReleasePhaseCode,
    NVL(sa.EBUNumeber, '') AS EBUNumeber,
    NVL(sa.PEOption, '') AS PEOption,
    NVL(sa.Application, '') AS Application,
    NVL(sa.EngVPIPjtName, '') AS EngVPIPjtName,
    NVL(sa.DPFPGM, '') AS DPFPGM,
    NVL(sa.DOCCanning, '') AS DOCCanning,
    NVL(sa.DPFCanning, '') AS DPFCanning,
    NVL(sa.ProductClassification, '') AS ProductClassification,
    wtp.UPDATESTAMPA2 AS UPDATESTAMPA2
FROM wcadmin.WTPART wtp
    INNER JOIN PART_MASTER_ALL pma
ON wtp.IDA2A2 = pma.MAX_PART_ID
    INNER JOIN wcadmin.WTPARTMASTER wpm
    ON pma.IDA3MASTERREFERENCE = wpm.IDA2A2
    LEFT JOIN STRING_ATTR_AGG sa
    ON wtp.IDA2A2 = sa.IDA3A4
    LEFT JOIN BOOLEAN_ATTR_AGG ba
    ON wtp.IDA2A2 = ba.IDA3A4
    LEFT JOIN UNIT_ATTR_AGG ua
    ON wtp.IDA2A2 = ua.IDA3A4
    LEFT JOIN TIMESTAMP_ATTR_AGG ta
    ON wtp.IDA2A2 = ta.IDA3A4