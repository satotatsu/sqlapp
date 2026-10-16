/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/** Explicit rebuild of an ordinary, nonpartitioned index. */
public class OracleRebuildIndexFactory extends SimpleSqlFactory<Index, OracleSqlBuilder> {
	static void validateOrdinaryIndex(Index index) {
		IndexType type = index.getIndexType();
		if (index.getName() == null || index.getName().isBlank()) throw new IllegalArgumentException("Index name is required");
		if (type != null && type != IndexType.BTree && type != IndexType.BitMap
				&& type != IndexType.Function && type != IndexType.FunctionBitmap)
			throw new IllegalArgumentException("Index lifecycle requires dedicated support for type: " + type);
	}
	static void validateRebuild(Index index) {
		validateOrdinaryIndex(index);
		if (index.getPartitioning() != null || "LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
			throw new IllegalArgumentException("Rebuild each index partition or subpartition separately: " + index.getName());
		validateNonTemporary(index);
		String status = index.getSpecifics().get(OracleAlterIndexFactory.STATUS);
		if (status != null && !"VALID".equals(status) && !"UNUSABLE".equals(status))
			throw new IllegalArgumentException("Unsupported index status for rebuild: " + status);
	}
	static void validateNonTemporary(Index index) {
		if (index.getTable() != null && index.getTable().getTableType() == Table.TableType.Temporary)
			throw new IllegalArgumentException("Cannot rebuild an index on a temporary table: " + index.getName());
	}
	@Override public List<SqlOperation> createSql(Index index) {
		if (OracleDomainIndex.isDomain(index)) {
			OracleDomainIndex.validate(index);
			if (index.getPartitioning() != null) throw new IllegalArgumentException("Rebuild individual domain index partitions through an Index/Table diff");
			if (getTableOptions().getOnlineIndex().test(index.getTable(), index))
				throw new IllegalArgumentException("Domain index rebuild does not support ONLINE");
			OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().index().space().name(index, getOptions().isDecorateSchemaName()).space()._add("REBUILD");
			return List.of(createOperation(builder.toString(), SqlType.REBUILD, index));
		}
		validateRebuild(index);
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().index().space().name(index, getOptions().isDecorateSchemaName()).space()._add("REBUILD");
		OracleIndexRebuildOptions.online(index, getDialect(), getTableOptions().getOnlineIndex().test(index.getTable(), index), false, false, builder);
		return List.of(createOperation(builder.toString(), SqlType.REBUILD, index));
	}
}
