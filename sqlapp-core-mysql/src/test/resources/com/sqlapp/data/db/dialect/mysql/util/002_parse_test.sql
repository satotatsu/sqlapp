use schema1/*#schemaNameSuffix*/;

SELECT
j.id,
j.object_id,
j.tenant_setting_id,
j.created_at,
COALESCE(ai.name, aid.name, 'UNKNOWN') AS name
FROM jobs j;

-- ###################################################################################################
--//@UNDO
use schema1/*#schemaNameSuffix*/;
SET SESSION FOREIGN_KEY_CHECKS=0;








