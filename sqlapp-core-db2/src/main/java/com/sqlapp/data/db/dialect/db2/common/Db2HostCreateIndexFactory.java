/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;

import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform;
import com.sqlapp.data.db.sql.CreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Host CREATE INDEX: named-column and expression keys, z/OS INCLUDE and
 * CLUSTER, IBM i sparse (WHERE) indexes, UNIQUE WHERE NOT NULL and explicit
 * partitioning. The index schema can differ from the table schema. Attributes
 * that the target product cannot express fail instead of being dropped.
 */
public class Db2HostCreateIndexFactory extends CreateIndexFactory {
	@Override
	protected boolean createIndex(Index index) {
		// Readers exclude constraint-owned indexes by catalog identity. An ordinary
		// index may legitimately have the same name as an unrelated constraint.
		return true;
	}

	@Override
	public void addObjectDetail(Index index, Table table, AbstractSqlBuilder<?> builder) {
		Platform platform = ((AbstractDb2HostDialect) getDialect()).getPlatform();
		if (table == null || index.getColumns().isEmpty() || !index.getDefinition().isEmpty()
				|| index.getIndexType() != null || index.getPartitioning() != null)
			throw new IllegalArgumentException("Host index requires a table and modeled keys: " + index.getName());
		boolean whereNotNull = flag(index, Db2HostIndexes.UNIQUE_WHERE_NOT_NULL);
		boolean cluster = flag(index, Db2HostIndexes.CLUSTER);
		String partitioned = index.getSpecifics().get(Db2HostIndexes.PARTITIONED);
		String where = index.getWhere() == null || index.getWhere().isBlank() ? null : index.getWhere().strip();
		if (whereNotNull && !index.isUnique())
			throw new IllegalArgumentException("UNIQUE WHERE NOT NULL requires a unique index: " + index.getName());
		if (!index.getIncludes().isEmpty() && (platform != Platform.ZOS || !index.isUnique()))
			throw new IllegalArgumentException(
					"INCLUDE columns require a unique Db2 for z/OS index: " + index.getName());
		if (where != null && platform != Platform.IBM_I)
			throw new IllegalArgumentException("Sparse (WHERE) indexes require Db2 for i: " + index.getName());
		if (cluster && platform != Platform.ZOS)
			throw new IllegalArgumentException("CLUSTER indexes require Db2 for z/OS: " + index.getName());
		boolean withoutOverlaps = flag(index, Db2HostIndexes.BUSINESS_TIME_WITHOUT_OVERLAPS);
		if (withoutOverlaps && (platform != Platform.ZOS || !index.isUnique() || !index.getIncludes().isEmpty()
				|| !Db2HostCreateTableFactory.hasBusinessTime(table)))
			throw new IllegalArgumentException(
					"BUSINESS_TIME WITHOUT OVERLAPS requires a unique z/OS index on a BUSINESS_TIME table: "
							+ index.getName());
		if (partitioned != null && !List.of("Y", "N").contains(partitioned))
			throw new IllegalArgumentException("Invalid index partitioning state: " + partitioned);
		builder.unique(index.isUnique());
		if (whereNotNull)
			builder._add(" WHERE NOT NULL");
		builder.index().space();
		String schema = index.getSpecifics().getOrDefault("INDEX_SCHEMA", index.getSchemaName());
		if (schema != null)
			builder.name(schema)._add(".");
		builder.name(index.getName()).on().name(table, true).space()._add("(");
		int count = 0;
		for (var column : index.getColumns()) {
			builder.comma(count++ > 0);
			key(column, builder);
		}
		if (withoutOverlaps)
			builder._add(", BUSINESS_TIME WITHOUT OVERLAPS");
		builder._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder._add(" INCLUDE (");
			count = 0;
			for (var column : index.getIncludes()) {
				if (!column.getStatement().isEmpty())
					throw new IllegalArgumentException("INCLUDE requires named columns: " + index.getName());
				builder.comma(count++ > 0).name(column.getName());
			}
			builder._add(")");
		}
		if (where != null)
			builder._add(" WHERE ")._add(where);
		if ("Y".equals(partitioned))
			builder._add(" PARTITIONED");
		else if ("N".equals(partitioned) && platform == Platform.IBM_I)
			builder._add(" NOT PARTITIONED");
		if (cluster)
			builder._add(" CLUSTER");
	}

	private void key(ReferenceColumn column, AbstractSqlBuilder<?> builder) {
		if (column.isIncludedColumn())
			throw new IllegalArgumentException("INCLUDE columns belong to Index.includes: " + column.getName());
		if (column.getNullsOrder() != null)
			throw new IllegalArgumentException("Host index keys cannot specify NULLS ordering: " + column.getName());
		var statement = column.getStatement();
		if (statement.isEmpty())
			builder.name(column.getName());
		else {
			if (statement.size() != 1 || statement.get(0) == null || statement.get(0).isBlank()
					|| statement.get(0).indexOf('\0') >= 0)
				throw new IllegalArgumentException("Index key expression must be one SQL expression");
			builder._add(statement.get(0).strip());
		}
		if (column.getOrder() == Order.Desc)
			builder._add(" DESC");
		else if (column.getOrder() != null && column.getOrder() != Order.Asc)
			throw new IllegalArgumentException("Unsupported host index key order: " + column.getOrder());
	}

	private static boolean flag(Index index, String key) {
		String value = index.getSpecifics().get(key);
		if (value != null && !"Y".equals(value))
			throw new IllegalArgumentException("Invalid " + key + ": " + value);
		return value != null;
	}
}
