SELECT id AS definition_id, text AS definition,
 (ISNULL(status,0) & 1) AS source_hidden, number AS source_group
FROM syscomments
WHERE texttype=0 AND id IN /*definitionIds*/(0)
ORDER BY id,number,colid2,colid
