/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Emits Informix index keys, clustering and optional access-method
 * configuration.
 */
public class InformixCreateIndexFactory extends AbstractCreateIndexFactory<AbstractSqlBuilder<?>> {
	@Override
	protected void addUnique(final Index index, final Table table, final AbstractSqlBuilder<?> builder) {
		builder.unique(index.isUnique());
		if (index.getIndexType() == IndexType.Clustered
				|| "true".equals(index.getSpecifics().get(InformixIndexReader.INFORMIX_CLUSTERED))) {
			builder.space()._add("CLUSTER");
		} else if (index.getIndexType() != null && index.getIndexType() != IndexType.BTree
				&& index.getIndexType() != IndexType.Function) {
			if (index.getIndexType() != IndexType.Other
					|| index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD) == null)
				throw new UnsupportedOperationException("Unsupported Informix index type: " + index.getIndexType());
		}
		builder.index().space();
		if (getOptions().isDecorateSchemaName()) {
			String owner = index.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER);
			if (owner == null)
				owner = index.getSchemaName();
			if (owner != null)
				builder.name(owner)._add('.');
		}
	}

	@Override
	public void addObjectDetail(final Index index, final Table table, final AbstractSqlBuilder<?> builder) {
		super.addObjectDetail(index, table, builder);
		final String method = index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD);
		final String owner = index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_METHOD_OWNER);
		final String parameters = index.getSpecifics().get(InformixIndexReader.INFORMIX_ACCESS_PARAMETERS);
		if (method == null) {
			if (owner != null || parameters != null)
				throw new IllegalArgumentException(
						"Informix access-method parameters/owner require a method name: " + index.getName());
			addStorage(index, builder);
			addMode(index, builder);
			return;
		}
		if (method.isBlank() || owner != null && owner.isBlank())
			throw new IllegalArgumentException(
					"Informix access-method identifiers must not be blank: " + index.getName());
		builder.space()._add("USING").space();
		if (owner != null)
			builder.name(owner)._add('.');
		builder.name(method);
		if (parameters != null && !parameters.isBlank())
			builder.space()._add('(')._add(parameters)._add(')');
		addStorage(index, builder);
		addMode(index, builder);
	}

	private void addStorage(final Index index, final AbstractSqlBuilder<?> builder) {
		final String override = index.getSpecifics().get(InformixIndexReader.INFORMIX_INDEX_STORAGE_OVERRIDE_REQUIRED);
		if (override != null && !java.util.Set.of("true", "false").contains(override))
			throw new IllegalArgumentException("Invalid Informix storage override flag: " + override);
		if ("true".equals(override) && index.getTableSpaceName() == null && index.getPartitioning() == null)
			throw new IllegalArgumentException("Cannot resolve actual Informix virtual index storage for "
					+ index.getName()
					+ "; set Index.tableSpaceName to the target sbspace/extspace (catalog descriptor location is not the data location)");
		final String attached = index.getSpecifics().get(InformixIndexReader.INFORMIX_INDEX_IN_TABLE);
		final String followsTable = index.getSpecifics().get(InformixIndexReader.INFORMIX_INDEX_FOLLOWS_TABLE);
		if (attached != null && !java.util.Set.of("true", "false").contains(attached))
			throw new IllegalArgumentException("Invalid Informix IN TABLE flag: " + attached);
		if (followsTable != null && !java.util.Set.of("true", "false").contains(followsTable))
			throw new IllegalArgumentException("Invalid Informix table-based index flag: " + followsTable);
		if ("true".equals(followsTable)) {
			if ("true".equals(attached) || index.getTableSpaceName() != null || index.getPartitioning() != null)
				throw new IllegalArgumentException(
						"Table-based Informix index cannot override its table storage: " + index.getName());
			return; // Omission preserves inheritance from the target table's fragmentation.
		}
		if ("true".equals(attached)) {
			if (index.getTableSpaceName() != null || index.getPartitioning() != null)
				throw new IllegalArgumentException(
						"Informix IN TABLE conflicts with detached index storage: " + index.getName());
			builder.space()._add("IN TABLE");
			return;
		}
		if (index.getPartitioning() == null) {
			if (index.getTableSpaceName() != null) {
				if (index.getTableSpaceName().isBlank())
					throw new IllegalArgumentException("Blank Informix index dbspace");
				builder.space()._add("IN").space()._add(quotedIdentifier(builder, index.getTableSpaceName()));
			}
			return;
		}
		final var partitioning = index.getPartitioning();
		if (index.getTableSpaceName() != null || !"E".equals(partitioning.getSpecifics()
				.get(com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.INFORMIX_FRAGMENT_STRATEGY)))
			throw new IllegalArgumentException(
					"Informix index fragmentation requires EXPRESSION and no single dbspace: " + index.getName());
		if (partitioning.getPartitions().isEmpty())
			throw new IllegalArgumentException("Informix index fragments are required: " + index.getName());
		builder.space()._add("FRAGMENT BY EXPRESSION");
		int position = 0;
		for (var partition : partitioning.getPartitions()) {
			final String expression = partition.getSpecifics()
					.get(com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.INFORMIX_FRAGMENT_EXPRESSION);
			if (expression == null || expression.isBlank() || partition.getTableSpaceName() == null
					|| partition.getTableSpaceName().isBlank())
				throw new IllegalArgumentException(
						"Informix index fragment requires expression and dbspace: " + partition.getName());
			builder.comma(position++ > 0).space();
			if (partition.getName() != null) {
				if (partition.getName().isBlank())
					throw new IllegalArgumentException("Blank Informix index partition name");
				builder._add("PARTITION").space()._add(quotedIdentifier(builder, partition.getName())).space();
			}
			builder._add(expression).space()._add("IN").space()
					._add(quotedIdentifier(builder, partition.getTableSpaceName()));
		}
	}

	private String quotedIdentifier(final AbstractSqlBuilder<?> builder, final String name) {
		return builder.isQuateObjectName() ? "\"" + name.replace("\"", "\"\"") + "\"" : name;
	}

	private void addMode(final Index index, final AbstractSqlBuilder<?> builder) {
		final String mode = com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.sql(index);
		if (mode != null)
			builder.space()._add(mode);
	}

	@Override
	protected void addColumn(final ReferenceColumn column, final AbstractSqlBuilder<?> builder) {
		if ("true".equals(column.getSpecifics().get(InformixIndexReader.INFORMIX_INDEX_EXPRESSION))) {
			builder._add(column.getName());
		} else {
			builder.name(column);
		}
		final String operatorClass = column.getSpecifics().get(InformixIndexReader.INFORMIX_OPERATOR_CLASS);
		final String owner = column.getSpecifics().get(InformixIndexReader.INFORMIX_OPERATOR_CLASS_OWNER);
		if (operatorClass == null && owner != null || operatorClass != null && operatorClass.isBlank()
				|| owner != null && owner.isBlank())
			throw new IllegalArgumentException("Informix operator-class name/owner is incomplete: " + column.getName());
		if (operatorClass != null) {
			builder.space();
			if (owner != null)
				builder.name(owner)._add('.');
			builder.name(operatorClass);
		}
		if (column.getOrder() == com.sqlapp.data.schemas.Order.Desc)
			builder.space()._add("DESC");
	}
}
