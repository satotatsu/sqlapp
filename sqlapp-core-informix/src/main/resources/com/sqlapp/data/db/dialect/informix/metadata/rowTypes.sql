SELECT TRIM(DBINFO('dbname')) AS catalog_name, TRIM(x.owner) AS schema_name,
 x.name AS type_name, x.extended_id AS type_id, p.name AS parent_name, TRIM(p.owner) AS parent_owner,
 x.mode AS type_mode, x.type AS base_type,
 CASE WHEN x.maxlen>0 THEN x.maxlen ELSE x.length END AS base_length,
 x.source AS source_id, s.name AS source_name, TRIM(s.owner) AS source_owner,
 a.fieldname AS field_name, a.type AS field_type, a.length AS field_length,
 a.seqno AS field_sequence, a.xtd_type_id AS field_type_id,
 CASE WHEN n.mode='C' THEN NULL ELSE n.name END AS field_type_name,
 TRIM(n.owner) AS field_type_owner, pa.fieldno AS inherited_field
FROM sysxtdtypes x
LEFT JOIN sysinherits h ON h.child=x.extended_id AND h.class='t'
LEFT JOIN sysxtdtypes p ON p.extended_id=h.parent
LEFT JOIN sysattrtypes a ON a.extended_id=x.extended_id AND a.levelno=1
LEFT JOIN sysattrtypes pa ON pa.extended_id=h.parent AND pa.levelno=1 AND pa.fieldno=a.fieldno
LEFT JOIN sysxtdtypes n ON n.extended_id=a.xtd_type_id
LEFT JOIN sysxtdtypes s ON s.extended_id=x.source
WHERE x.mode='C' OR (x.mode IN ('R','D') AND x.extended_id>=2048 AND x.name IS NOT NULL
 /*if complexTypesOnly == true*/ AND 1=0 /*end*/
 /*if isNotEmpty(schemaName)*/ AND x.owner IN /*schemaName*/('%') /*end*/
 /*if isNotEmpty(typeName)*/ AND x.name IN /*typeName*/('%') /*end*/
)
ORDER BY x.extended_id,a.seqno
