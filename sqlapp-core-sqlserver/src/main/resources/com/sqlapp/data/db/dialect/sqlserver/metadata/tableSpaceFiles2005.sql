SELECT
  DB_NAME() AS catalog_name
, df.file_id
, df.file_guid
, df.type
, df.type_desc
, df.data_space_id
, f.name AS file_group_name
, df.name
, df.physical_name
, df.size
, df.state_desc
, df.size
, df.max_size
, df.growth
, df.is_media_read_only
, df.is_read_only
, df.is_sparse
, df.is_percent_growth
, df.is_name_reserved
, df.create_lsn
, df.drop_lsn
, df.read_only_lsn
, df.read_write_lsn
, df.differential_base_lsn
, df.differential_base_guid
, df.differential_base_time
, df.redo_start_lsn
, df.redo_start_fork_guid
, df.redo_target_lsn
, df.redo_target_fork_guid
, df.backup_lsn
FROM sys.database_files df
LEFT OUTER JOIN sys.filegroups f
  ON (df.data_space_id=f.data_space_id)
WHERE 1=1
  /*if isNotEmpty(tableSpaceName) */
  AND f.name IN /*tableSpaceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY df.file_id