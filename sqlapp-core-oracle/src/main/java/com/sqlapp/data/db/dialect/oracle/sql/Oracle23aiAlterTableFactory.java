/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

/** ALTER TABLE handling for data use case domain references. */
public class Oracle23aiAlterTableFactory extends OracleAlterTableFactory {
	private boolean sameDomain(Column original, Column target) {
		return Objects.equals(original.getDomainName(), target.getDomainName())
				&& Objects.equals(original.getDomainSchemaName(), target.getDomainSchemaName());
	}

	private boolean rowDomain(Column column) {
		return column != null && column.getDomain() != null && (!column.getDomain().getColumns().isEmpty()
				|| OracleDomainAssociationUtils.isFlexible(column.getDomain()));
	}

	@Override
	protected void addColumnDefinitions(Map<String, Difference<?>> allDiff, Table original, Table target,
			DbObjectDifferenceCollection differences, List<SqlOperation> result) {
		for (var difference : differences.getList(State.Added, State.Deleted, State.Modified)) {
			Column before = difference.getOriginal(Column.class);
			Column after = difference.getTarget(Column.class);
			if (before != null && after != null && before.getDomainName() != null && after.getDomainName() == null
					&& after.getDataType() == null && after.getDataTypeName() == null) {
				throw new IllegalArgumentException(
						"Removing a domain requires the physical column type: " + after.getName());
			}
			if (before != null && after != null && !Objects.equals(before.getName(), after.getName())
					&& (before.getDomainName() != null || after.getDomainName() != null)
					&& difference.getProperties().size() > 1) {
				throw new IllegalArgumentException(
						"Rename a domain-associated column separately from other changes: " + before.getName());
			}
		}
		var oldGroups = OracleDomainAssociationUtils.groups(original);
		var newGroups = OracleDomainAssociationUtils.groups(target);
		for (var entry : oldGroups.entrySet()) {
			if (!sameGroup(entry.getValue(), newGroups.get(entry.getKey())))
				addGroupChange(target, entry.getValue(), false, result);
		}
		super.addColumnDefinitions(allDiff, original, target, differences, result);
		for (var entry : newGroups.entrySet()) {
			if (!sameGroup(entry.getValue(), oldGroups.get(entry.getKey())))
				addGroupChange(target, entry.getValue(), true, result);
		}
	}

	@Override
	protected void addModifyColumn(Table original, Table target, List<DbObjectDifference> differences,
			List<SqlOperation> result) {
		for (var difference : differences) {
			Column before = difference.getOriginal(Column.class);
			Column after = difference.getTarget(Column.class);
			if (before.getDomainName() == null || rowDomain(before) || sameDomain(before, after))
				continue;
			if (after.getDomainName() == null && after.getDataType() == null && after.getDataTypeName() == null) {
				throw new IllegalArgumentException(
						"Removing a domain requires the physical column type: " + after.getName());
			}
			var builder = createSqlBuilder();
			builder.alter().table().space().name(target, getOptions().isDecorateSchemaName());
			builder.space()._add("MODIFY (").name(before.getName())._add(") DROP DOMAIN");
			add(result, createOperation(builder.toString(), SqlType.ALTER, before, after));
		}
		super.addModifyColumn(original, target, differences, result);
	}

	private boolean sameGroup(Map<String, Column> first, Map<String, Column> second) {
		if (second == null || !first.keySet().equals(second.keySet()))
			return false;
		return OracleDomainAssociationUtils.orderedColumns(first).stream().map(Column::getName).toList()
				.equals(OracleDomainAssociationUtils.orderedColumns(second).stream().map(Column::getName).toList());
	}

	private void addGroupChange(Table table, Map<String, Column> parts, boolean attach, List<SqlOperation> result) {
		var columns = OracleDomainAssociationUtils.orderedColumns(parts);
		var domain = columns.get(0).getDomain();
		if (OracleDomainAssociationUtils.isFlexible(domain)
				&& !(getDialect() instanceof com.sqlapp.data.db.dialect.oracle.Oracle26ai)) {
			throw new IllegalArgumentException("Flexible domain associations require Oracle26ai: " + domain.getName());
		}
		var builder = createSqlBuilder();
		builder.alter().table().space().name(table, getOptions().isDecorateSchemaName()).space()._add("MODIFY (");
		int i = 0;
		for (Column column : columns)
			builder.comma(i++ > 0).name(column.getName());
		builder._add(")").space()._add(attach ? "ADD DOMAIN" : "DROP DOMAIN");
		if (attach)
			builder.space().name(domain, true);
		add(result, createOperation(builder.toString(), SqlType.ALTER, columns));
	}

}
