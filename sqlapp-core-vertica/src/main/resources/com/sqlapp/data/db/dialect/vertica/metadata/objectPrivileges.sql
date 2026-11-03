SELECT
  CURRENT_DATABASE() AS catalog_name
, g.grantor
, g.grantee
, g.object_schema AS schema_name
, g.object_name
, g.object_type
, g.privileges_description
FROM V_CATALOG.GRANTS g
WHERE g.object_type!='ROLE'
  /*if isNotEmpty(objectName) */
  AND g.object_name IN /*objectName*/('%')
  /*end*/
ORDER BY g.grantor, g.grantee, g.object_schema, g.object_name
