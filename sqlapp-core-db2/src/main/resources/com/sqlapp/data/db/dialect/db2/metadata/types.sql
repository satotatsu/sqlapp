SELECT
    d.TYPESCHEMA AS schema_name
  , d.TYPENAME AS type_name
  , d.*
FROM SYSCAT.DATATYPES d
WHERE 1=1
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
