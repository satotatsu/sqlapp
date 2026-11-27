/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.*;

/** Rebuilds complete RANGE/LIST child routing without deleting rows. */
final class OracleCompositeChildRouting {
	private OracleCompositeChildRouting() {
	}

	private static void require(boolean valid, String message) {
		if (!valid)
			throw new IllegalArgumentException(message);
	}

	private static String literal(String text) {
		return "'" + text.replace("'", "''") + "'";
	}

	private static String id(String name, Dialect dialect, OracleSqlBuilder builder) {
		return dialect.isQuoted(name) ? dialect.unQuote(name)
				: builder.isQuateObjectName() ? name : name.toUpperCase(java.util.Locale.ROOT);
	}

	static void validate(Partitioning info, int position, int count, Dialect dialect) {
		validate(info, position, count, dialect, true);
	}

	static void validate(Partitioning info, int position, int count, Dialect dialect, boolean complete) {
		boolean hash = info.getSubPartitioningType() == PartitioningType.Hash;
		boolean list = info.getSubPartitioningType() == PartitioningType.List;
		require(hash || list || info.getSubPartitioningType() == PartitioningType.Range, "Unsupported child routing");
		if (!hash)
			require(info.getSubPartitioningColumns().size() == 1, "Parent routing restoration requires one child key");
		for (int p = position; p < position + count; p++) {
			var children = info.getPartitions().get(p).getSubPartitions();
			require(!children.isEmpty(), "Parent routing requires children");
			if (!hash && complete)
				require(list ? OracleListPartitionValues.isDefault(children.get(children.size() - 1).getHighValue())
						: "MAXVALUE".equalsIgnoreCase(children.get(children.size() - 1).getHighValue()),
						"Parent routing restoration requires a final DEFAULT or MAXVALUE child");
			var listValues = new ArrayList<String>();
			for (int i = 0; i < children.size(); i++) {
				SubPartition child = children.get(i);
				if (hash) {
					require(child.getHighValue() == null, "HASH children cannot have boundaries");
					continue;
				}
				require(child.getHighValue() != null && !child.getHighValue().isBlank(), "Child boundary is required");
				require(child.getHighValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 4000,
						"Child boundary exceeds the supported 4000-byte comparison limit");
				if (i + 1 < children.size() || list && !OracleListPartitionValues.isDefault(child.getHighValue())) {
					require(child.getHighValue() != null && !child.getHighValue().isBlank(),
							"Child boundary is required");
					if (list) {
						listValues.add(child.getHighValue());
						OracleListPartitionValues.same(String.join(",", listValues), String.join(",", listValues));
					} else
						require(!"MAXVALUE".equalsIgnoreCase(child.getHighValue()),
								"Only the final child may be MAXVALUE");
				}
				// Collapsing and splitting must not silently change individual physical
				// settings.
				SubPartition a = children.get(0).clone(), b = child.clone();
				b.setName(a.getName());
				b.setSchemaName(a.getSchemaName());
				b.setHighValue(a.getHighValue());
				b.setTableSpaceName(a.getTableSpaceName());
				a.getStatistics().clear();
				b.getStatistics().clear();
				require(a.diff(b).toDifference().getChangedProperties(dialect).isEmpty(),
						"Routing restoration requires common child physical attributes except tablespace");
			}
		}
	}

	/**
	 * Checks the native split's inherited boundaries without changing its routing.
	 */
	static String checkSplit(Table table, int position, Dialect dialect, Supplier<OracleSqlBuilder> builders) {
		var builder = builders.get();
		String owner = table.getSchemaName() == null ? "SYS_CONTEXT('USERENV','CURRENT_SCHEMA')"
				: literal(id(table.getSchemaName(), dialect, builder));
		var requests = new ArrayList<String>();
		for (int p = position; p < position + 2; p++) {
			Partition parent = table.getPartitioning().getPartitions().get(p);
			for (int j = 0; j < parent.getSubPartitions().size(); j++)
				requests.add("SELECT " + literal(id(parent.getName(), dialect, builder)) + " parent_name, " + (j + 1)
						+ " position_no, " + literal(parent.getSubPartitions().get(j).getHighValue())
						+ " expected_bound FROM DUAL");
		}
		return """
				DECLARE
				  found NUMBER := 0;
				BEGIN
				  FOR observed IN (
				    WITH requested AS ($REQUESTS)
				    SELECT p.HIGH_VALUE, r.expected_bound FROM ALL_TAB_SUBPARTITIONS p JOIN requested r
				      ON r.parent_name=p.PARTITION_NAME AND r.position_no=p.SUBPARTITION_POSITION
				      WHERE p.TABLE_OWNER=$OWNER AND p.TABLE_NAME=$OBJECT
				  ) LOOP
				    IF observed.HIGH_VALUE IS NULL OR TRIM(observed.HIGH_VALUE) <> TRIM(observed.expected_bound) THEN
				      RAISE_APPLICATION_ERROR(-20017, 'Native composite split changed inherited child boundaries');
				    END IF;
				    found := found + 1;
				  END LOOP;
				  IF found <> $COUNT THEN RAISE_APPLICATION_ERROR(-20018, 'Native composite split child set is incomplete'); END IF;
				END;"""
				.replace("$REQUESTS", String.join("\nUNION ALL\n", requests)).replace("$OWNER", owner)
				.replace("$OBJECT", literal(id(table.getName(), dialect, builder)))
				.replace("$COUNT", Integer.toString(requests.size()));
	}

	static String create(Table table, int position, int count, Dialect dialect, Supplier<OracleSqlBuilder> builders) {
		var builder = builders.get();
		String owner = table.getSchemaName() == null ? "SYS_CONTEXT('USERENV','CURRENT_SCHEMA')"
				: literal(id(table.getSchemaName(), dialect, builder));
		String object = literal(id(table.getName(), dialect, builder));
		var parents = new ArrayList<String>();
		var commands = new StringBuilder();
		boolean list = table.getPartitioning().getSubPartitioningType() == PartitioningType.List;
		for (int p = position; p < position + count; p++) {
			Partition parent = table.getPartitioning().getPartitions().get(p);
			String name = literal(id(parent.getName(), dialect, builder));
			parents.add(name);
			commands.append("\n  survivor := NULL;\n  FOR i IN 1..children.COUNT LOOP\n    IF children(i).parent_name=")
					.append(name).append(" THEN\n")
					.append("      IF survivor IS NOT NULL THEN\n        EXECUTE IMMEDIATE command || ' MERGE SUBPARTITIONS ' || q(survivor) || ', ' || q(children(i).child_name) || ' INTO SUBPARTITION ' || q(children(i).child_name) || ' UPDATE INDEXES';\n      END IF;\n      survivor := children(i).child_name;\n    END IF;\n  END LOOP;\n")
					.append("  IF survivor IS NULL THEN RAISE_APPLICATION_ERROR(-20015, 'Composite routing parent missing'); END IF;\n");
			for (int j = 0; j + 1 < parent.getSubPartitions().size(); j++) {
				String key = table.getSchemaName() + ":" + table.getName() + ":" + p + ":" + j;
				String token = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))
						.toString().replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT);
				String left = "SQLAPP_L_" + token, right = "SQLAPP_R_" + token;
				String suffix = (list ? " VALUES (" : " AT (") + parent.getSubPartitions().get(j).getHighValue()
						+ ") INTO (SUBPARTITION \"" + left + "\", SUBPARTITION \"" + right + "\") UPDATE INDEXES";
				commands.append("  EXECUTE IMMEDIATE command || ' SPLIT SUBPARTITION ' || q(survivor) || ")
						.append(literal(suffix)).append(";\n  survivor := ").append(literal(right)).append(";\n");
			}
		}
		return """
				DECLARE
				  TYPE child_record IS RECORD(parent_name VARCHAR2(128), child_name VARCHAR2(128));
				  TYPE child_list IS TABLE OF child_record;
				  children child_list;
				  survivor VARCHAR2(128);
				  command VARCHAR2(32767);
				  FUNCTION q(name VARCHAR2) RETURN VARCHAR2 IS
				  BEGIN RETURN '"' || REPLACE(name, '"', '""') || '"'; END;
				BEGIN
				  SELECT PARTITION_NAME, SUBPARTITION_NAME BULK COLLECT INTO children
				    FROM ALL_TAB_SUBPARTITIONS WHERE TABLE_OWNER=$OWNER AND TABLE_NAME=$OBJECT
				      AND PARTITION_NAME IN ($PARENTS) ORDER BY PARTITION_NAME, SUBPARTITION_POSITION;
				  command := 'ALTER TABLE ' || q($OWNER) || '.' || q($OBJECT);
				  FOR observed IN (SELECT p.HIGH_VALUE FROM ALL_TAB_SUBPARTITIONS p
				    WHERE p.TABLE_OWNER=$OWNER AND p.TABLE_NAME=$OBJECT AND p.PARTITION_NAME IN ($PARENTS)
				      AND NOT EXISTS (SELECT 1 FROM ALL_TAB_SUBPARTITIONS n WHERE n.TABLE_OWNER=p.TABLE_OWNER
				        AND n.TABLE_NAME=p.TABLE_NAME AND n.PARTITION_NAME=p.PARTITION_NAME
				        AND n.SUBPARTITION_POSITION>p.SUBPARTITION_POSITION)) LOOP
				    IF observed.HIGH_VALUE IS NULL OR UPPER(TRIM(observed.HIGH_VALUE)) <> '$TERMINAL' THEN
				      RAISE_APPLICATION_ERROR(-20016, 'Composite template produced incomplete child routing');
				    END IF;
				  END LOOP;
				$COMMANDS
				END;""".replace("$OWNER", owner).replace("$OBJECT", object)
				.replace("$PARENTS", String.join(",", parents)).replace("$TERMINAL", list ? "DEFAULT" : "MAXVALUE")
				.replace("$COMMANDS", commands);
	}
}
