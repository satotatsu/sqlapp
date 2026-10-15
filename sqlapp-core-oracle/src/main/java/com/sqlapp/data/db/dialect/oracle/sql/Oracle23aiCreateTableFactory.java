/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Oracle Database 23ai CREATE TABLE extensions.
 */
public class Oracle23aiCreateTableFactory extends OracleCreateTableFactory {
	public static final String DOMAIN_ASSOCIATION_ID = "oracle.domain.associationId";

	@Override
	protected void addConstraintDefinitions(Table table, OracleSqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		Map<List<String>, Map<String, Column>> groups = new LinkedHashMap<>();
		for (Column column : table.getColumns()) {
			if (column.getDomain() == null || column.getDomain().getColumns().isEmpty()) continue;
			String group = column.getSpecifics().get(DOMAIN_ASSOCIATION_ID);
			var key = java.util.Arrays.asList(column.getDomainSchemaName(), column.getDomainName(), group);
			var parts = groups.computeIfAbsent(key, k -> new LinkedHashMap<>());
			String part = column.getDomainColumnName() == null ? column.getName() : column.getDomainColumnName();
			if (parts.putIfAbsent(part, column) != null) {
				throw new IllegalArgumentException("Duplicate domain component; set a distinct associationId for repeated associations: " + part);
			}
		}
		for (var parts : groups.values()) {
			var domain = parts.values().iterator().next().getDomain();
			if (parts.size() != domain.getColumns().size()) {
				throw new IllegalArgumentException("Incomplete domain association: " + domain.getName());
			}
			builder.lineBreak().comma().space()._add("DOMAIN").space().name(domain, true)._add("(");
			int i = 0;
			boolean flexible = OracleDomainAssociationUtils.isFlexible(domain);
			for (var component : domain.getColumns()) {
				if (flexible && OracleDomainAssociationUtils.isDiscriminant(component)) continue;
				Column column = parts.get(component.getName());
				if (column == null) throw new IllegalArgumentException("Missing domain component: " + component.getName());
				builder.comma(i++ > 0).name(column.getName());
			}
			builder._add(")");
			if (flexible) {
				long discriminants = domain.getColumns().stream().filter(OracleDomainAssociationUtils::isDiscriminant).count();
				if (discriminants == 0 || i == 0) throw new IllegalArgumentException("Flexible domain requires value and USING components: " + domain.getName());
				builder.space()._add("USING (");
				i = 0;
				for (var component : domain.getColumns()) {
					if (!OracleDomainAssociationUtils.isDiscriminant(component)) continue;
					Column column = parts.get(component.getName());
					if (column == null) throw new IllegalArgumentException("Missing domain discriminant: " + component.getName());
					builder.comma(i++ > 0).name(column.getName());
				}
				builder._add(")");
			}
		}
	}

	@Override
	protected void addCreateObject(final Table table, final OracleSqlBuilder builder) {
		builder.create().table().ifNotExists(this.getOptions().isCreateIfNotExists()).space();
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		super.addOtherDefinitions(table, result);
		if (!OracleAnnotationUtils.getAnnotations(table).isEmpty()) {
			final OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space().name(table, this.getOptions().isDecorateSchemaName());
			OracleAnnotationUtils.addAnnotations(builder, table);
			addSql(result, builder, SqlType.ALTER, table);
		}
		for (Column column : table.getColumns()) {
			if (OracleAnnotationUtils.getAnnotations(column).isEmpty()) {
				continue;
			}
			final OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().table().space().name(table, this.getOptions().isDecorateSchemaName()).space()._add("MODIFY")
					.space().name(column);
			OracleAnnotationUtils.addAnnotations(builder, column);
			addSql(result, builder, SqlType.ALTER, column);
		}
	}
}
