SELECT 
    tc.*
  , kc.column_name
  , kc.position_in_unique_constraint
  , kc.ordinal_position
  , si.asc_or_desc
  , si.index_name
  , si.filter_condition
FROM information_schema.table_constraints tc
INNER JOIN information_schema.key_column_usage kc
  ON (tc.table_catalog=kc.table_catalog
  AND tc.table_schema=kc.table_schema
  AND tc.table_name=kc.table_name
  AND tc.constraint_name=kc.constraint_name
  )
LEFT OUTER JOIN information_schema.system_indexinfo si
  ON (kc.table_catalog=si.table_cat
  AND kc.table_schema=si.table_schem
  AND kc.table_name=si.table_name
  AND si.index_name IN ('SYS_IDX_' || kc.constraint_name || '_%')
  AND si.non_unique=false
  AND kc.column_name=si.column_name
  )
WHERE tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
  /*if isNotEmpty(catalogName)*/
  AND tc.constraint_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND tc.constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND tc.constraint_name IN /*constraintName*/('%')
  /*end*/
ORDER BY tc.constraint_schema, tc.table_name, tc.constraint_type
  , tc.constraint_name, kc.ordinal_position
