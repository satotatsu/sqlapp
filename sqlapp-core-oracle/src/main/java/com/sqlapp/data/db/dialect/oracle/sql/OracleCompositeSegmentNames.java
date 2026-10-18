/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.*;

/**
 * Restores HASH child counts and generated names using two batched catalog
 * queries.
 */
final class OracleCompositeSegmentNames {
	private OracleCompositeSegmentNames() {
	}

	private static String literal(String value) {
		return value == null ? "CAST(NULL AS VARCHAR2(128))" : "'" + value.replace("'", "''") + "'";
	}

	private static String identifier(String name, Dialect dialect, OracleSqlBuilder builder) {
		if (name == null)
			return null;
		return dialect.isQuoted(name) ? dialect.unQuote(name)
				: builder.isQuateObjectName() ? name : name.toUpperCase(Locale.ROOT);
	}

	static String create(Table table, int position, int count, Dialect dialect, Supplier<OracleSqlBuilder> builders) {
		var requests = new ArrayList<String>();
		OracleSqlBuilder naming = builders.get();
		add(requests, "T", table.getSchemaName(), table.getName(), table.getPartitioning(), position, count, dialect,
				naming);
		for (Index index : table.getIndexes())
			if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				add(requests, "I", index.getSchemaName(), index.getName(), index.getPartitioning(), position, count,
						dialect, naming);
		return BLOCK.replace("$COUNT", Integer.toString(requests.size())).replace("$REQUESTS",
				String.join("\nUNION ALL\n", requests));
	}

	private static void add(ArrayList<String> requests, String kind, String owner, String name, Partitioning info,
			int position, int count, Dialect dialect, OracleSqlBuilder naming) {
		owner = identifier(owner, dialect, naming);
		name = identifier(name, dialect, naming);
		for (int i = position; i < position + count; i++) {
			Partition parent = info.getPartitions().get(i);
			for (int j = 0; j < parent.getSubPartitions().size(); j++) {
				SubPartition child = parent.getSubPartitions().get(j);
				String identity = kind + ":" + owner + ":" + name + ":" + i + ":" + j;
				String temp = "SQLAPP_C_"
						+ java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))
								.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
				requests.add("SELECT " + literal(kind) + " kind, "
						+ (owner == null ? "SYS_CONTEXT('USERENV','CURRENT_SCHEMA')" : literal(owner)) + " owner_name, "
						+ literal(name) + " object_name, " + literal(identifier(parent.getName(), dialect, naming))
						+ " parent_name, " + (j + 1) + " position_no, "
						+ literal(identifier(child.getName(), dialect, naming)) + " target_name, " + literal(temp)
						+ " temp_name, " + literal(identifier(child.getTableSpaceName(), dialect, naming))
						+ " target_space, " + parent.getSubPartitions().size() + " expected_count FROM DUAL");
			}
		}
	}

	private static final String BLOCK = """
			DECLARE
			  TYPE segment_record IS RECORD (kind VARCHAR2(1), owner_name VARCHAR2(128), object_name VARCHAR2(128),
			    old_name VARCHAR2(128), target_name VARCHAR2(128), temp_name VARCHAR2(128),
			    old_space VARCHAR2(128), target_space VARCHAR2(128), old_state VARCHAR2(8),
			    conflict NUMBER, actual_count NUMBER, expected_count NUMBER);
			  TYPE segment_list IS TABLE OF segment_record;
			  segments segment_list;
			  TYPE parent_record IS RECORD (owner_name VARCHAR2(128), object_name VARCHAR2(128), parent_name VARCHAR2(128),
			    actual_count NUMBER, expected_count NUMBER);
			  TYPE parent_list IS TABLE OF parent_record;
			  parents parent_list;
			  command VARCHAR2(32767);
			  FUNCTION q(p_name VARCHAR2) RETURN VARCHAR2 IS
			  BEGIN RETURN '"' || REPLACE(p_name, '"', '""') || '"'; END;
			BEGIN
			  WITH requested AS (
			$REQUESTS
			  ), expected_parents AS (
			    SELECT owner_name, object_name, parent_name, expected_count FROM requested WHERE kind='T'
			      GROUP BY owner_name, object_name, parent_name, expected_count
			  )
			  SELECT r.owner_name, r.object_name, r.parent_name, COUNT(p.SUBPARTITION_NAME), r.expected_count
			    BULK COLLECT INTO parents
			    FROM expected_parents r LEFT JOIN ALL_TAB_SUBPARTITIONS p ON p.TABLE_OWNER=r.owner_name
			      AND p.TABLE_NAME=r.object_name AND p.PARTITION_NAME=r.parent_name
			    GROUP BY r.owner_name, r.object_name, r.parent_name, r.expected_count;
			  FOR i IN 1..parents.COUNT LOOP
			    IF parents(i).actual_count=0 OR parents(i).expected_count<1 THEN
			      RAISE_APPLICATION_ERROR(-20013, 'Composite parent missing or child count invalid');
			    END IF;
			    command := 'ALTER TABLE ' || q(parents(i).owner_name) || '.' || q(parents(i).object_name)
			      || ' MODIFY PARTITION ' || q(parents(i).parent_name);
			    WHILE parents(i).actual_count < parents(i).expected_count LOOP
			      EXECUTE IMMEDIATE command || ' ADD SUBPARTITION UPDATE INDEXES';
			      parents(i).actual_count := parents(i).actual_count + 1;
			    END LOOP;
			    WHILE parents(i).actual_count > parents(i).expected_count LOOP
			      EXECUTE IMMEDIATE command || ' COALESCE SUBPARTITION UPDATE INDEXES';
			      parents(i).actual_count := parents(i).actual_count - 1;
			    END LOOP;
			  END LOOP;
			  WITH requested AS (
			$REQUESTS
			  ), catalog AS (
			    SELECT 'T' kind, TABLE_OWNER owner_name, TABLE_NAME object_name, PARTITION_NAME parent_name,
			      SUBPARTITION_NAME child_name, SUBPARTITION_POSITION position_no, TABLESPACE_NAME space_name, 'USABLE' state
			      FROM ALL_TAB_SUBPARTITIONS
			    UNION ALL
			    SELECT 'I', INDEX_OWNER, INDEX_NAME, PARTITION_NAME, SUBPARTITION_NAME, SUBPARTITION_POSITION, TABLESPACE_NAME, STATUS
			      FROM ALL_IND_SUBPARTITIONS
			  )
			  SELECT p.kind, p.owner_name, p.object_name, p.child_name, r.target_name, r.temp_name, p.space_name, r.target_space, p.state,
			    CASE WHEN EXISTS (SELECT 1 FROM requested a WHERE a.kind=p.kind AND a.owner_name=p.owner_name AND a.object_name=p.object_name
			      AND a.temp_name=r.target_name) OR EXISTS (SELECT 1 FROM catalog x WHERE x.kind=p.kind AND x.owner_name=p.owner_name AND x.object_name=p.object_name
			      AND (x.child_name=r.temp_name OR (x.child_name=r.target_name AND NOT EXISTS (
			        SELECT 1 FROM requested a WHERE a.kind=x.kind AND a.owner_name=x.owner_name AND a.object_name=x.object_name
			          AND a.parent_name=x.parent_name AND a.position_no=x.position_no)))) THEN 1 ELSE 0 END,
			    (SELECT COUNT(*) FROM catalog x WHERE x.kind=p.kind AND x.owner_name=p.owner_name AND x.object_name=p.object_name AND x.parent_name=p.parent_name),
			    r.expected_count
			    BULK COLLECT INTO segments
			    FROM catalog p JOIN requested r ON r.kind=p.kind AND r.owner_name=p.owner_name AND r.object_name=p.object_name
			      AND r.parent_name=p.parent_name AND r.position_no=p.position_no
			    ORDER BY p.kind DESC, p.owner_name, p.object_name, p.parent_name, p.position_no;
			  IF segments.COUNT <> $COUNT THEN RAISE_APPLICATION_ERROR(-20011, 'Composite child set is incomplete: ' || segments.COUNT || '/$COUNT'); END IF;
			  FOR i IN 1..segments.COUNT LOOP
			    IF segments(i).conflict <> 0 OR segments(i).actual_count <> segments(i).expected_count THEN
			      RAISE_APPLICATION_ERROR(-20012, 'Composite child names or count conflict');
			    END IF;
			  END LOOP;
			  FOR i IN 1..segments.COUNT LOOP
			    command := CASE segments(i).kind WHEN 'T' THEN 'ALTER TABLE ' ELSE 'ALTER INDEX ' END
			      || q(segments(i).owner_name) || '.' || q(segments(i).object_name);
			    EXECUTE IMMEDIATE command || ' RENAME SUBPARTITION ' || q(segments(i).old_name) || ' TO ' || q(segments(i).temp_name);
			  END LOOP;
			  FOR i IN 1..segments.COUNT LOOP
			    command := CASE segments(i).kind WHEN 'T' THEN 'ALTER TABLE ' ELSE 'ALTER INDEX ' END
			      || q(segments(i).owner_name) || '.' || q(segments(i).object_name);
			    EXECUTE IMMEDIATE command || ' RENAME SUBPARTITION ' || q(segments(i).temp_name) || ' TO ' || q(segments(i).target_name);
			    IF segments(i).target_space IS NOT NULL AND (segments(i).old_space IS NULL OR segments(i).old_space <> segments(i).target_space)
			       OR segments(i).kind='I' AND segments(i).old_state <> 'USABLE' THEN
			      command := command || CASE segments(i).kind WHEN 'T' THEN ' MOVE SUBPARTITION ' ELSE ' REBUILD SUBPARTITION ' END || q(segments(i).target_name);
			      IF segments(i).target_space IS NOT NULL THEN command := command || ' TABLESPACE ' || q(segments(i).target_space); END IF;
			      IF segments(i).kind='T' THEN command := command || ' UPDATE INDEXES'; END IF;
			      EXECUTE IMMEDIATE command;
			    END IF;
			  END LOOP;
			END;""";
}
