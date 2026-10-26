/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.sql.CreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Db2 LUW indexes, including non-key INCLUDE columns. */
public class Db2CreateIndexFactory extends CreateIndexFactory {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Index index) {
		java.util.List<com.sqlapp.data.db.sql.SqlOperation> nativeSql;
		if (!index.getDefinition().isEmpty()) {
			if (!createIndex(index))
				return java.util.List.of();
			nativeSql = new java.util.ArrayList<>();
			for (String sql : Db2DefinitionSql.statements(index.getDefinition(), getDialect())) {
				if (sql == null || sql.isBlank())
					throw new IllegalArgumentException("Db2 index definition must be executable SQL");
				addSql(nativeSql, createSqlBuilder()._add(sql), com.sqlapp.data.db.sql.SqlType.CREATE, index);
			}
		} else
			nativeSql = super.createSql(index);
		if (nativeSql.isEmpty())
			return nativeSql;
		var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		if (index.getDefinition().isEmpty()
				&& index.getColumns().stream().anyMatch(column -> !column.getStatement().isEmpty())
				|| !index.getDefinition().isEmpty() && getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)
			for (String sql : com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.sql(null,
					index.getSpecifics().get("FUNC_PATH")))
				addSql(result, createSqlBuilder()._add(sql), com.sqlapp.data.db.sql.SqlType.SET_SEARCH_PATH_TO_SCHEMA,
						index);
		result.addAll(nativeSql);
		if (index.getRemarks() != null) {
			String schema = index.getSpecifics().getOrDefault("INDEX_SCHEMA", index.getSchemaName());
			String name = schema == null
					? com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.identifier(index.getName())
					: com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(schema, index.getName());
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON INDEX " + name + " IS '" + index.getRemarks().replace("'", "''") + "'"),
					com.sqlapp.data.db.sql.SqlType.SET_COMMENT, index);
		}
		return result;
	}

	@Override
	protected void addColumn(ReferenceColumn column, AbstractSqlBuilder<?> builder) {
		addKeyValue(column, builder);
		String rawOrder = column.getSpecifics().get("DB2_COLORDER");
		if ("R".equals(rawOrder)) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
				throw new IllegalArgumentException("Db2 RANDOM index recreation requires LUW 11.1 or later");
			builder.space()._add("RANDOM");
		} else if (column.getOrder() != null && column.getOrder() != com.sqlapp.data.schemas.Order.Asc)
			builder.space()._add(column.getOrder());
	}

	private void addKeyValue(ReferenceColumn column, AbstractSqlBuilder<?> builder) {
		String order = column.getSpecifics().get("DB2_COLORDER");
		if (order != null && !java.util.Set.of("A", "D", "I", "R").contains(order))
			throw new IllegalArgumentException("Invalid Db2 index key order: " + order);
		if ("S".equals(column.getSpecifics().get("DB2_VIRTUAL")))
			throw new IllegalArgumentException("Db2 statistical-view index requires a complete native definition");
		if (column.getStatement().isEmpty())
			addIdentifier(column.getName(), builder);
		else {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
				throw new IllegalArgumentException("Db2 expression index requires LUW 11.1 or later");
			builder._add("(")._add(Db2DefinitionSql.text(column.getStatement()))._add(")");
		}
	}

	private void addIdentifier(String name, AbstractSqlBuilder<?> builder) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			String quoted = com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.identifier(name);
			if (name.indexOf('"') >= 0) {
				builder._add(quoted);
				return;
			}
		}
		builder.name(name);
	}

	@Override
	protected boolean createIndex(Index index) {
		Table table = index.getTable();
		if (table == null && getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)
			return true;
		if (table != null) {
			for (var constraint : table.getConstraints().getUniqueConstraints()) {
				if (constraint.isEnable() && java.util.Objects.equals(index.getName(), constraint.getIndex().getName()))
					return false;
			}
		}
		return super.createIndex(index);
	}

	@Override
	public void addObjectDetail(Index index, Table table, AbstractSqlBuilder<?> builder) {
		String nativeType = index.getSpecifics().get("INDEXTYPE");
		if (nativeType != null)
			nativeType = nativeType.stripTrailing();
		boolean xml = "XVIL".equals(nativeType) && getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110;
		if (xml)
			Db2XmlIndexSql.validate(index, table);
		if (nativeType != null && !xml && !java.util.Set.of("REG", "CLUS").contains(nativeType))
			throw new IllegalArgumentException(
					"Db2 specialized index requires a complete native definition: " + nativeType);
		if (index.getIndexType() != null && index.getIndexType() != IndexType.BTree
				&& index.getIndexType() != IndexType.Clustered) {
			throw new IllegalArgumentException("Unsupported Db2 index type: " + index.getIndexType());
		}
		long keys = index.getColumns().stream().filter(c -> !c.isIncludedColumn()).count();
		if (keys == 0 || table == null) {
			throw new IllegalArgumentException("Db2 index requires a table and key columns: " + index.getName());
		}
		boolean includes = index.getColumns().stream().anyMatch(ReferenceColumn::isIncludedColumn);
		if (includes && !index.isUnique()) {
			throw new IllegalArgumentException("Db2 INCLUDE requires a unique index: " + index.getName());
		}
		builder.unique(index.isUnique()).index().space();
		String schema = index.getSpecifics().get("INDEX_SCHEMA");
		if (schema == null) {
			schema = index.getSchemaName();
		}
		if (schema != null && (getOptions().isDecorateSchemaName() || !schema.equals(table.getSchemaName()))) {
			addIdentifier(schema, builder);
			builder._add(".");
		}
		addIdentifier(index.getName(), builder);
		builder.on();
		if ((getOptions().isDecorateSchemaName() || "SESSION".equals(table.getSchemaName())
				|| schema != null && !schema.equals(table.getSchemaName())) && table.getSchemaName() != null) {
			addIdentifier(table.getSchemaName(), builder);
			builder._add(".");
		}
		addIdentifier(table.getName(), builder);
		builder.space()._add("(");
		int count = 0;
		for (ReferenceColumn column : index.getColumns()) {
			if (!column.isIncludedColumn()) {
				builder.comma(count++ > 0);
				addColumn(column, builder);
			}
		}
		builder.space()._add(")");
		if (xml)
			Db2XmlIndexSql.append(index, builder);
		if (includes) {
			builder.space()._add("INCLUDE (");
			count = 0;
			for (ReferenceColumn column : index.getColumns()) {
				if (column.isIncludedColumn()) {
					builder.comma(count++ > 0);
					addKeyValue(column, builder);
				}
			}
			builder.space()._add(")");
		}
		if (index.getIndexType() == IndexType.Clustered) {
			builder.space()._add("CLUSTER");
		}
		for (String option : new String[] { "PCTFREE", "MINPCTUSED" }) {
			String value = index.getSpecifics().get(option);
			if (value != null) {
				int number = Integer.parseInt(value);
				// The catalog uses -1 when PCTFREE was not explicitly declared.
				if ("PCTFREE".equals(option) && number == -1)
					continue;
				if (number < 0 || number > 99)
					throw new IllegalArgumentException("Db2 " + option + " must be between 0 and 99");
				builder.space()._add(option).space()._add(number);
			}
		}
		String reverse = index.getSpecifics().get("REVERSE_SCANS");
		if (reverse != null) {
			if (!java.util.Set.of("Y", "N").contains(reverse))
				throw new IllegalArgumentException("Invalid Db2 reverse scan flag");
			builder.space()._add("Y".equals(reverse) ? "ALLOW REVERSE SCANS" : "DISALLOW REVERSE SCANS");
		}
		String compression = index.getSpecifics().get("COMPRESSION");
		if (compression != null || index.isCompression()) {
			if (compression != null && !java.util.Set.of("Y", "N").contains(compression))
				throw new IllegalArgumentException("Invalid Db2 index compression flag");
			builder.space()._add(index.isCompression() ? "COMPRESS YES" : "COMPRESS NO");
		}
		String nullKeys = index.getSpecifics().get("NULLKEYS");
		if (nullKeys != null) {
			if (!java.util.Set.of("Y", "N").contains(nullKeys))
				throw new IllegalArgumentException("Invalid Db2 index null-key flag");
			if ("N".equals(nullKeys))
				builder.space()._add("EXCLUDE NULL KEYS");
		}
	}
}
