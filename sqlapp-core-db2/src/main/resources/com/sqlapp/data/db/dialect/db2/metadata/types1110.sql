SELECT
    d.TYPESCHEMA AS schema_name
  , d.TYPENAME AS type_name
  , CASE WHEN cursor_target.DEPENDENCIES IS NULL THEN 'W' WHEN cursor_target.DEPENDENCIES=1 AND cursor_target.ROW_DEPENDENCIES=1 THEN 'S' ELSE 'U' END AS CURSOR_KIND
  , cursor_target.ROW_SCHEMA AS CURSOR_ROW_SCHEMA, cursor_target.ROW_MODULE AS CURSOR_ROW_MODULE, cursor_target.ROW_NAME AS CURSOR_ROW_NAME
  , d.*
FROM SYSCAT.DATATYPES d
LEFT JOIN (
 SELECT dep.TYPESCHEMA,dep.TYPEMODULENAME,dep.TYPENAME,COUNT(*) AS DEPENDENCIES,
        SUM(CASE WHEN dep.BTYPE='R' AND target.METATYPE='F' THEN 1 ELSE 0 END) AS ROW_DEPENDENCIES,
        MAX(CASE WHEN dep.BTYPE='R' AND target.METATYPE='F' THEN target.TYPESCHEMA END) AS ROW_SCHEMA,
        MAX(CASE WHEN dep.BTYPE='R' AND target.METATYPE='F' THEN target.TYPEMODULENAME END) AS ROW_MODULE,
        MAX(CASE WHEN dep.BTYPE='R' AND target.METATYPE='F' THEN target.TYPENAME END) AS ROW_NAME
 FROM SYSCAT.DATATYPEDEP dep
 LEFT JOIN SYSCAT.DATATYPES target ON dep.BTYPE='R' AND target.TYPESCHEMA=dep.BSCHEMA
  AND COALESCE(target.TYPEMODULENAME,'')=COALESCE(dep.BMODULENAME,'') AND target.TYPENAME=dep.BNAME
 GROUP BY dep.TYPESCHEMA,dep.TYPEMODULENAME,dep.TYPENAME
) cursor_target ON cursor_target.TYPESCHEMA=d.TYPESCHEMA AND cursor_target.TYPENAME=d.TYPENAME
 AND COALESCE(cursor_target.TYPEMODULENAME,'')=COALESCE(d.TYPEMODULENAME,'')
WHERE d.TYPEMODULEID IS NULL
  AND d.OWNERTYPE <> 'S' AND d.OWNER <> 'SYSIBM'
  /*if isNotEmpty(schemaName) */
  AND rtrim(d.TYPESCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(typeName) */
  AND rtrim(d.TYPENAME) IN /*typeName*/('%')
  /*end*/
  --A = ユーザー定義配列タイプ 
  --C = ユーザー定義カーソル・タイプ
  --F = ユーザー定義行タイプ 
  --L = ユーザー定義連想配列タイプ 
  --R = ユーザー定義構造化タイプ 
  --S = システムで定義済みのタイプ
  --T = ユーザー定義特殊タイプ 
  AND d.METATYPE IN ('C', 'F', 'R')
ORDER BY d.TYPESCHEMA, d.TYPENAME
WITH UR
