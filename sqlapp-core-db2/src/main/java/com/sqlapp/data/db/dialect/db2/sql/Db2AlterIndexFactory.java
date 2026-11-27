/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Native changes that preserve the physical index identity. */
public class Db2AlterIndexFactory extends SimpleSqlFactory<Index, Db2SqlBuilder> {
	static boolean canAlter(DbObjectDifference difference) {
		if (difference.getState() != State.Modified)
			return false;
		Index before = difference.getOriginal(Index.class).clone(), after = difference.getTarget(Index.class).clone();
		// The shared compression property is canonical; the raw catalog flag is
		// supplemental.
		before.getSpecifics().remove("COMPRESSION");
		after.getSpecifics().remove("COMPRESSION");
		return Set.of(SchemaProperties.COMPRESSION.getLabel(), SchemaProperties.REMARKS.getLabel())
				.containsAll(before.diff(after).getChangedProperties().keySet());
	}

	@Override
	public List<SqlOperation> createSql(Index index) {
		throw new IllegalArgumentException("Db2 ALTER INDEX requires original and target models");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() != State.Modified) {
			Index index = difference.getState() == State.Deleted ? difference.getOriginal(Index.class)
					: difference.getTarget(Index.class);
			SqlFactory<Index> factory = getSqlFactoryRegistry().getSqlFactory(index,
					difference.getState() == State.Deleted ? SqlType.DROP : SqlType.CREATE);
			return factory.createSql(index);
		}
		if (!canAlter(difference))
			throw new IllegalArgumentException("Db2 structural index changes require an explicit recreation plan");
		Index before = difference.getOriginal(Index.class), after = difference.getTarget(Index.class);
		String schema = after.getSpecifics().getOrDefault("INDEX_SCHEMA", after.getSchemaName());
		String name = schema == null ? Db2CreationContext.identifier(after.getName())
				: Db2CreationContext.qualified(schema, after.getName());
		List<SqlOperation> result = new ArrayList<>();
		if (before.isCompression() != after.isCompression()) {
			String nativeType = after.getSpecifics().get("INDEXTYPE");
			if (nativeType != null)
				nativeType = nativeType.stripTrailing();
			Table table = after.getTable();
			if (!after.getDefinition().isEmpty() || nativeType != null && !Set.of("REG", "CLUS").contains(nativeType)
					|| after.getIndexType() != null && after.getIndexType() != IndexType.BTree
							&& after.getIndexType() != IndexType.Clustered
					|| table != null && (table.getTableType() == Table.TableType.Temporary
							|| "SESSION".equals(table.getSchemaName())))
				throw new IllegalArgumentException(
						"Db2 index compression ALTER is unsupported for specialized, temporary or full-definition indexes");
			result.add(createOperation("ALTER INDEX " + name + " COMPRESS " + (after.isCompression() ? "YES" : "NO"),
					SqlType.ALTER, after));
		}
		if (!Objects.equals(before.getRemarks(), after.getRemarks()))
			result.add(createOperation(Db2AlterCommentSql.comment(getDialect(), "INDEX " + name, after.getRemarks()),
					SqlType.SET_COMMENT, after));
		return result;
	}
}
