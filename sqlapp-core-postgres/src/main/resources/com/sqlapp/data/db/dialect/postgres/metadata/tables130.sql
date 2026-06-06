WITH autovacuum_settings AS (
    SELECT
        max(CASE WHEN name='autovacuum'
                 THEN setting END) AS autovacuum
      , max(CASE WHEN name = 'autovacuum_vacuum_threshold'
            THEN setting END) AS autovacuum_vacuum_threshold
      , max(CASE WHEN name = 'autovacuum_vacuum_insert_threshold'
            THEN setting END) AS autovacuum_vacuum_insert_threshold
      , max(CASE WHEN name = 'autovacuum_vacuum_scale_factor'
            THEN setting END) AS autovacuum_vacuum_scale_factor
      , max(CASE WHEN name = 'autovacuum_vacuum_insert_scale_factor'
            THEN setting END) AS autovacuum_vacuum_insert_scale_factor
      , max(CASE WHEN name = 'autovacuum_vacuum_cost_delay'
            THEN setting END) AS autovacuum_vacuum_cost_delay
      , max(CASE WHEN name = 'autovacuum_vacuum_cost_limit'
            THEN setting END) AS autovacuum_vacuum_cost_limit
      , max(CASE WHEN name = 'autovacuum_freeze_min_age'
            THEN setting END) AS autovacuum_freeze_min_age
      , max(CASE WHEN name = 'autovacuum_freeze_max_age'
            THEN setting END) AS autovacuum_freeze_max_age
      , max(CASE WHEN name = 'autovacuum_freeze_table_age'
            THEN setting END) AS autovacuum_freeze_table_age
      , max(CASE WHEN name = 'autovacuum_multixact_freeze_min_age'
            THEN setting END) AS autovacuum_multixact_freeze_min_age
      , max(CASE WHEN name = 'autovacuum_multixact_freeze_max_age'
            THEN setting END) AS autovacuum_multixact_freeze_max_age
      , max(CASE WHEN name = 'autovacuum_multixact_freeze_table_age'
            THEN setting END) AS autovacuum_multixact_freeze_table_age
      , max(CASE WHEN name = 'autovacuum_analyze_threshold'
            THEN setting END) AS autovacuum_analyze_threshold
      , max(CASE WHEN name = 'autovacuum_analyze_scale_factor'
            THEN setting END) AS autovacuum_analyze_scale_factor
    FROM pg_settings
    WHERE name LIKE 'autovacuum%'
)
SELECT
    current_database() AS catalog_name
  , n.nspname AS schema_name
  , c.relname AS table_name
  , CASE c.relkind
      WHEN 'r' THEN 'TABLE' 
      WHEN 'i' THEN 'INDEX' 
      WHEN 'S' THEN 'SEQUENCE' 
      WHEN 'v' THEN 'VIEW' 
      WHEN 'c' THEN 'TYPE'
      WHEN 'f' THEN 'FOREIGN TABLE'
      WHEN 'm' THEN 'MATERIALIZED VIEW'
      WHEN 'p' THEN 'PARTITIONED TABLE'
      ELSE NULL
    END AS table_type
  , d.description AS remarks
  , c.oid AS table_id
  , sp.spcname
  /*FOR VALUES IN ('aa', 'bb')*/
  , pg_get_expr(c.relpartbound, c.oid) AS partition_expression
  , pg_get_partkeydef(c.oid) AS partition_strategy_column
  , pg_get_partition_constraintdef(c.oid)
  , c.*
  , c.relpages::bigint * 8 * 1024 AS data_length
  , case partstrat 
    when 'l' then 'list' 
    when 'r' then 'range'
    end as partition_strategy
  , pn.nspname AS parent_schema_name
  , pc.relname AS parent_table_name
  , av.fillfactor
  , CASE
  WHEN lower(coalesce(av.autovacuum_enabled, avs.autovacuum))
       IN ('on','true','1')
  THEN true
  ELSE false
  END AS autovacuum_enabled
  , coalesce(av.autovacuum_vacuum_threshold, avs.autovacuum_vacuum_threshold) as autovacuum_vacuum_threshold
  , coalesce(av.autovacuum_vacuum_insert_threshold, avs.autovacuum_vacuum_insert_threshold) as autovacuum_vacuum_insert_threshold
  , coalesce(av.autovacuum_vacuum_scale_factor, avs.autovacuum_vacuum_scale_factor) as autovacuum_vacuum_scale_factor
  , coalesce(av.autovacuum_vacuum_insert_scale_factor, avs.autovacuum_vacuum_insert_scale_factor) as autovacuum_vacuum_insert_scale_factor
  , coalesce(av.autovacuum_vacuum_cost_delay, avs.autovacuum_vacuum_cost_delay) as autovacuum_vacuum_cost_delay
  , coalesce(av.autovacuum_vacuum_cost_limit, avs.autovacuum_vacuum_cost_limit) as autovacuum_vacuum_cost_limit
  , coalesce(av.autovacuum_freeze_min_age, avs.autovacuum_freeze_min_age) as autovacuum_freeze_min_age
  , coalesce(av.autovacuum_freeze_max_age, avs.autovacuum_freeze_max_age) as autovacuum_freeze_max_age
  , coalesce(av.autovacuum_freeze_table_age, avs.autovacuum_freeze_table_age) as autovacuum_freeze_table_age
  , coalesce(av.autovacuum_multixact_freeze_min_age, avs.autovacuum_multixact_freeze_min_age) as autovacuum_multixact_freeze_min_age
  , coalesce(av.autovacuum_multixact_freeze_max_age, avs.autovacuum_multixact_freeze_max_age) as autovacuum_multixact_freeze_max_age
  , coalesce(av.autovacuum_multixact_freeze_table_age, avs.autovacuum_multixact_freeze_table_age) as autovacuum_multixact_freeze_table_age
  , coalesce(av.autovacuum_analyze_threshold, avs.autovacuum_analyze_threshold) as autovacuum_analyze_threshold
  , coalesce(av.autovacuum_analyze_scale_factor, avs.autovacuum_analyze_scale_factor) as autovacuum_analyze_scale_factor
  , tstat.*
FROM pg_catalog.pg_class c 
INNER JOIN pg_catalog.pg_namespace n
  ON (c.relnamespace = n.oid)
LEFT OUTER JOIN pg_catalog.pg_description d 
  ON (c.oid = d.objoid
  AND d.objsubid = 0) 
LEFT OUTER JOIN pg_tablespace sp
  ON (c.reltablespace = sp.oid)
LEFT OUTER JOIN pg_stat_all_tables tstat
  ON (c.oid = tstat.relid)
LEFT OUTER JOIN pg_partitioned_table pt
  ON (c.oid=pt.partrelid) 
LEFT OUTER JOIN pg_inherits i
  ON (i.inhrelid = c.oid
  AND c.relpartbound IS NOT NULL)
LEFT OUTER JOIN pg_catalog.pg_class pc
  ON (i.inhparent = pc.oid)
LEFT OUTER JOIN pg_catalog.pg_namespace pn
  ON (pc.relnamespace = pn.oid)
LEFT JOIN LATERAL (
    SELECT
        max(CASE WHEN option_name = 'fillfactor'
            THEN option_value END) AS fillfactor
      , max(CASE WHEN option_name = 'autovacuum_enabled'
            THEN option_value END) AS autovacuum_enabled
      , max(CASE WHEN option_name = 'autovacuum_vacuum_threshold'
            THEN option_value END) AS autovacuum_vacuum_threshold
      , max(CASE WHEN option_name = 'autovacuum_vacuum_insert_threshold'
            THEN option_value END) AS autovacuum_vacuum_insert_threshold
      , max(CASE WHEN option_name = 'autovacuum_vacuum_scale_factor'
            THEN option_value END) AS autovacuum_vacuum_scale_factor
      , max(CASE WHEN option_name = 'autovacuum_vacuum_insert_scale_factor'
            THEN option_value END) AS autovacuum_vacuum_insert_scale_factor
      , max(CASE WHEN option_name = 'autovacuum_vacuum_cost_delay'
            THEN option_value END) AS autovacuum_vacuum_cost_delay
      , max(CASE WHEN option_name = 'autovacuum_vacuum_cost_limit'
            THEN option_value END) AS autovacuum_vacuum_cost_limit
      , max(CASE WHEN option_name = 'autovacuum_freeze_min_age'
            THEN option_value END) AS autovacuum_freeze_min_age
      , max(CASE WHEN option_name = 'autovacuum_freeze_max_age'
            THEN option_value END) AS autovacuum_freeze_max_age
      , max(CASE WHEN option_name = 'autovacuum_freeze_table_age'
            THEN option_value END) AS autovacuum_freeze_table_age
      , max(CASE WHEN option_name = 'autovacuum_multixact_freeze_min_age'
            THEN option_value END) AS autovacuum_multixact_freeze_min_age
      , max(CASE WHEN option_name = 'autovacuum_multixact_freeze_max_age'
            THEN option_value END) AS autovacuum_multixact_freeze_max_age
      , max(CASE WHEN option_name = 'autovacuum_multixact_freeze_table_age'
            THEN option_value END) AS autovacuum_multixact_freeze_table_age
      , max(CASE WHEN option_name = 'autovacuum_analyze_threshold'
            THEN option_value END) AS autovacuum_analyze_threshold
      , max(CASE WHEN option_name = 'autovacuum_analyze_scale_factor'
            THEN option_value END) AS autovacuum_analyze_scale_factor
    FROM pg_options_to_table(c.reloptions)
) av ON true
CROSS JOIN (
    SELECT *
    FROM autovacuum_settings
) avs
WHERE 1=1
  /*if isNotEmpty(oid)*/
  AND c.oid IN /*oid*/(1)
  /*end*/
  /*if isNotEmpty(relkind)*/
  AND c.relkind::varchar IN /*relkind*/('r','p')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.relname IN /*tableName*/('%')
  /*end*/
ORDER BY n.nspname, c.relname