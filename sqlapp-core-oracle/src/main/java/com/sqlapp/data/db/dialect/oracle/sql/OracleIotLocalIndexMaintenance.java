/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Table;

/**
 * Resolves Oracle-assigned names once, then maintains the affected IOT
 * secondary segments.
 */
final class OracleIotLocalIndexMaintenance {
	private OracleIotLocalIndexMaintenance() {
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new IllegalArgumentException(message);
	}

	private static String literal(String value) {
		return value == null ? "CAST(NULL AS VARCHAR2(128))" : "'" + value.replace("'", "''") + "'";
	}

	private static String identifier(String value, Dialect dialect, OracleSqlBuilder builder) {
		if (value == null)
			return null;
		if (dialect.isQuoted(value))
			return dialect.unQuote(value);
		return builder.isQuateObjectName() ? value : value.toUpperCase(Locale.ROOT);
	}

	private static String temporary(String owner, String name, int position) {
		try {
			String identity = String.valueOf(owner) + "\u0000" + name + "\u0000" + position;
			return "SQLAPP_IOT_" + HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)))
					.substring(0, 16).toUpperCase(Locale.ROOT);
		} catch (java.security.NoSuchAlgorithmException error) {
			throw new IllegalStateException(error);
		}
	}

	private static void segment(Partition part, Dialect dialect) {
		String status = part.getSpecifics().get("oracle.index.status");
		require(Set.of("USABLE", "UNUSABLE").contains(status == null ? "" : status),
				"Local segment state must be explicitly USABLE or UNUSABLE");
		Partition expected = new Partition(part.getName()).setHighValue(part.getHighValue())
				.setTableSpaceName(part.getTableSpaceName());
		expected.setCompression(part.isCompression());
		expected.setCompressionType(part.getCompressionType());
		expected.getSpecifics().put("oracle.index.status", status);
		if (part.getSpecifics().containsKey("oracle.index.compression"))
			expected.getSpecifics().put("oracle.index.compression", "DISABLED");
		require(part.getName() != null && !part.getName().isBlank()
				&& OracleTablePartitionMaintenance.same(expected, part, dialect),
				"Unsupported local secondary segment attributes");
	}

	private static String compression(Index index, Partition part, Dialect dialect,
			Supplier<OracleSqlBuilder> builders) {
		Partition resolved = part.clone();
		String mode = OracleIndexPartitionCompression.mode(part, OracleIndexPartitionCompression.rootMode(index));
		require(!mode.startsWith("ADVANCED") || OracleIndexPartitionCompression.rootMode(index).startsWith("ADVANCED"),
				"Advanced partition compression requires advanced compression on the index");
		resolved.getSpecifics().remove("oracle.index.compression");
		resolved.setCompression(!"DISABLED".equals(mode));
		resolved.setCompressionType(mode);
		OracleSqlBuilder builder = builders.get();
		OracleIndexPartitionCompression.add(index, resolved, dialect, builder);
		return builder.toString();
	}

	static String create(Table before, Table after, int position, boolean merge, Dialect dialect,
			Supplier<OracleSqlBuilder> builders) {
		List<String> requests = new ArrayList<>();
		OracleSqlBuilder naming = builders.get();
		for (Index index : before.getIndexes()) {
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				continue;
			Index target = after.getIndexes().get(index.getName());
			OracleIndexCompression.add(index, dialect, builders.get());
			var oldParts = index.getPartitioning().getPartitions();
			var parts = target.getPartitioning().getPartitions();
			require(oldParts.size() == before.getPartitioning().getPartitions().size()
					&& parts.size() == after.getPartitioning().getPartitions().size(),
					"Local index partitions must correspond to the IOT partitions");
			for (int i = 0; i < position; i++)
				require(OracleTablePartitionMaintenance.same(oldParts.get(i), parts.get(i), dialect),
						"Unaffected local partitions must remain unchanged");
			int oldAffected = merge ? 2 : 1, newAffected = merge ? 1 : 2;
			for (int i = position + oldAffected; i < oldParts.size(); i++)
				require(OracleTablePartitionMaintenance.same(oldParts.get(i), parts.get(i - oldAffected + newAffected),
						dialect), "Unaffected local partitions must remain unchanged");
			boolean compressedSource = !"DISABLED".equals(OracleIndexPartitionCompression.rootMode(index));
			for (int i = 0; i < oldAffected; i++) {
				Partition source = oldParts.get(position + i);
				segment(source, dialect);
				compression(index, source, dialect, builders);
				compressedSource |= !"DISABLED".equals(
						OracleIndexPartitionCompression.mode(source, OracleIndexPartitionCompression.rootMode(index)));
			}
			var names = new java.util.HashSet<String>();
			for (Partition part : parts)
				require(names.add(identifier(part.getName(), dialect, naming)),
						"Local partition names must be unique under the quoting policy");
			String owner = identifier(index.getSchemaName(), dialect, naming),
					name = identifier(index.getName(), dialect, naming);
			for (int i = 0; i < newAffected; i++) {
				int ordinal = position + i + 1;
				Partition part = parts.get(position + i);
				segment(part, dialect);
				String tablespace = part.getTableSpaceName() == null ? target.getTableSpaceName()
						: part.getTableSpaceName();
				String compression = compression(target, part, dialect, builders);
				boolean rebuild = "USABLE".equals(part.getSpecifics().get("oracle.index.status")) || tablespace != null
						|| compressedSource || !"DISABLED".equals(OracleIndexPartitionCompression.mode(part,
								OracleIndexPartitionCompression.rootMode(target)));
				require(java.util.Objects.equals(part.getHighValue(),
						after.getPartitioning().getPartitions().get(position + i).getHighValue()),
						"Local segment boundary must match its IOT partition");
				requests.add("SELECT " + (owner == null ? "SYS_CONTEXT('USERENV','CURRENT_SCHEMA')" : literal(owner))
						+ " owner_name, " + literal(name) + " index_name, " + ordinal + " position_no, "
						+ literal(identifier(part.getName(), dialect, naming)) + " target_name, "
						+ literal(temporary(owner, name, ordinal)) + " temp_name, "
						+ literal(identifier(tablespace, dialect, naming)) + " target_space, "
						+ literal(part.getSpecifics().get("oracle.index.status")) + " target_state, "
						+ literal(compression) + " compression_clause, " + (rebuild ? 1 : 0) + " rebuild_required, "
						+ parts.size() + " expected_count FROM DUAL");
			}
		}
		if (requests.isEmpty())
			return null;
		return BLOCK.replace("$COUNT", Integer.toString(requests.size())).replace("$REQUESTS",
				String.join("\nUNION ALL\n", requests));
	}

	private static final String BLOCK = """
			DECLARE
			  TYPE segment_record IS RECORD (
			    owner_name VARCHAR2(128), index_name VARCHAR2(128), old_name VARCHAR2(128),
			    target_name VARCHAR2(128), temp_name VARCHAR2(128), target_space VARCHAR2(128),
			    target_state VARCHAR2(8), compression_clause VARCHAR2(128), rebuild_required NUMBER,
			    conflict NUMBER, actual_count NUMBER, expected_count NUMBER);
			  TYPE segment_list IS TABLE OF segment_record;
			  segments segment_list;
			  command VARCHAR2(32767);
			  FUNCTION q(p_name VARCHAR2) RETURN VARCHAR2 IS
			  BEGIN RETURN '"' || REPLACE(p_name, '"', '""') || '"'; END;
			BEGIN
			  WITH requested AS (
			$REQUESTS
			  )
			  SELECT p.INDEX_OWNER, p.INDEX_NAME, p.PARTITION_NAME, r.target_name, r.temp_name,
			         r.target_space, r.target_state, r.compression_clause, r.rebuild_required,
			         CASE WHEN EXISTS (SELECT 1 FROM ALL_IND_PARTITIONS x
			           WHERE x.INDEX_OWNER=p.INDEX_OWNER AND x.INDEX_NAME=p.INDEX_NAME
			             AND (x.PARTITION_NAME=r.temp_name OR (x.PARTITION_NAME=r.target_name
			               AND NOT EXISTS (SELECT 1 FROM requested a WHERE a.owner_name=x.INDEX_OWNER
			                 AND a.index_name=x.INDEX_NAME AND a.position_no=x.PARTITION_POSITION))))
			           THEN 1 ELSE 0 END,
			         (SELECT COUNT(*) FROM ALL_IND_PARTITIONS x WHERE x.INDEX_OWNER=p.INDEX_OWNER AND x.INDEX_NAME=p.INDEX_NAME),
			         r.expected_count
			    BULK COLLECT INTO segments
			    FROM ALL_IND_PARTITIONS p JOIN requested r ON r.owner_name=p.INDEX_OWNER
			      AND r.index_name=p.INDEX_NAME AND r.position_no=p.PARTITION_POSITION
			    ORDER BY p.INDEX_OWNER, p.INDEX_NAME, p.PARTITION_POSITION;
			  IF segments.COUNT <> $COUNT THEN
			    RAISE_APPLICATION_ERROR(-20001, 'IOT local index partition set is incomplete');
			  END IF;
			  FOR i IN 1..segments.COUNT LOOP
			    IF segments(i).conflict <> 0 OR segments(i).actual_count <> segments(i).expected_count THEN
			      RAISE_APPLICATION_ERROR(-20002, 'IOT local index partition names or count conflict');
			    END IF;
			  END LOOP;
			  FOR i IN 1..segments.COUNT LOOP
			    command := 'ALTER INDEX ' || q(segments(i).owner_name) || '.' || q(segments(i).index_name);
			    EXECUTE IMMEDIATE command || ' RENAME PARTITION ' || q(segments(i).old_name) || ' TO ' || q(segments(i).temp_name);
			  END LOOP;
			  FOR i IN 1..segments.COUNT LOOP
			    command := 'ALTER INDEX ' || q(segments(i).owner_name) || '.' || q(segments(i).index_name);
			    EXECUTE IMMEDIATE command || ' RENAME PARTITION ' || q(segments(i).temp_name) || ' TO ' || q(segments(i).target_name);
			    IF segments(i).rebuild_required = 1 THEN
			      command := command || ' REBUILD PARTITION ' || q(segments(i).target_name);
			      IF segments(i).target_space IS NOT NULL THEN command := command || ' TABLESPACE ' || q(segments(i).target_space); END IF;
			      command := command || segments(i).compression_clause;
			      EXECUTE IMMEDIATE command;
			    END IF;
			    IF segments(i).target_state = 'UNUSABLE' THEN
			      command := 'ALTER INDEX ' || q(segments(i).owner_name) || '.' || q(segments(i).index_name)
			        || ' MODIFY PARTITION ' || q(segments(i).target_name) || ' UNUSABLE';
			      EXECUTE IMMEDIATE command;
			    END IF;
			  END LOOP;
			END;""";
}
