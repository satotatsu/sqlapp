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
		if (index.getTableSpaceName() != null)
			builder.space()._add("IN").space().name(index.getTableSpaceName());
		if (method == null) {
			if (owner != null || parameters != null)
				throw new IllegalArgumentException(
						"Informix access-method parameters/owner require a method name: " + index.getName());
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
		addMode(index, builder);
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
