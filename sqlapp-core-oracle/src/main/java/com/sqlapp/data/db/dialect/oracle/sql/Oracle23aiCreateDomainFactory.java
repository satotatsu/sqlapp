/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainCheckConstraint;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE factory for Oracle Database 23ai data use case domains.
 */
public class Oracle23aiCreateDomainFactory extends OracleCreateDomainFactory {

	@Override
	protected java.util.List<Domain> sort(java.util.List<Domain> domains) {
		return OracleDomainDependencies.sort(domains);
	}

	public static final String STRICT = "STRICT";
	public static final String DEFAULT_ON_NULL = "DEFAULT_ON_NULL";
	public static final String CONSTRAINT_NAME = "CONSTRAINT_NAME";
	public static final String DISPLAY = "DISPLAY";
	public static final String ORDER = "ORDER";
	public static final String VALIDATED = "VALIDATED";
	public static final String RELY = "RELY";

	@Override
	protected boolean supportsDomainChecks() { return true; }
	@Override
	protected boolean supportsDomainDefinition() { return true; }
	@Override
	protected boolean supportsDomainColumns() { return true; }

	@Override
	protected void addCreateObject(final Domain domain, final OracleSqlBuilder builder) {
		if (!CommonUtils.isEmpty(domain.getDefinition())) {
			builder._add(domain.getDefinition());
			return;
		}
		validate(domain);
		builder.create().space()._add("DOMAIN").space();
		builder.ifNotExists(this.getOptions().isCreateIfNotExists()).space();
		builder.name(domain, this.getOptions().isDecorateSchemaName());
		builder.space().as().space();
		if (domain.getColumns().isEmpty()) {
			addColumnDefinition(domain, builder);
		} else {
			builder._add("(");
			int i = 0;
			for (var column : domain.getColumns()) {
				builder.comma(i++ > 0).space().name(column.getName()).space().as().space();
				addColumnDefinition(column, builder);
				OracleAnnotationUtils.addAnnotations(builder, column);
			}
			builder._add(")");
		}
		addChecks(domain, builder);
		addExpression(domain, builder, DISPLAY);
		addExpression(domain, builder, ORDER);
		OracleAnnotationUtils.addAnnotations(builder, domain);
	}

	private void addColumnDefinition(AbstractColumn<?> domain, OracleSqlBuilder builder) {
		if (domain.getDataType() == DataType.DECIMAL && domain.getLength() == null) {
			// The generic decimal generator supplies defaults; an unbounded Oracle NUMBER must retain its scale semantics.
			builder._add("NUMBER");
			if (domain.getScale() != null) {
				builder._add("(*,")._add(domain.getScale())._add(")");
			}
		} else {
			builder.typeDefinition(domain.getDataType(), domain.getDataTypeName(), domain.getLength(), domain.getScale());
		}
		if (Boolean.TRUE.equals(domain.getSpecifics().get(STRICT, Boolean.class))) {
			builder.space()._add("STRICT");
		}
		if (domain.getDefaultValue() != null) {
			builder.space()._add("DEFAULT");
			if (Boolean.TRUE.equals(domain.getSpecifics().get(DEFAULT_ON_NULL, Boolean.class))) {
				builder.space()._add("ON NULL");
			}
			builder.space()._add(domain.getDefaultValue());
		}
		if (domain.getCollation() != null) {
			builder.space()._add("COLLATE").space().name(domain.getCollation());
		}
		if (domain.isNotNull()) {
			builder.space()._add("NOT NULL");
		}
	}

	private void addChecks(Domain domain, OracleSqlBuilder builder) {
		if (domain.getCheck() != null) {
			final String constraintName = CommonUtils.trim(domain.getSpecifics().get(CONSTRAINT_NAME));
			if (!CommonUtils.isEmpty(constraintName)) {
				builder.space()._add("CONSTRAINT").space().name(constraintName);
			}
			builder.space()._add("CHECK").space()._add("(")._add(domain.getCheck())._add(")");
			addDeferrability(builder, domain.getDeferrability());
		}
		for (DomainCheckConstraint check : domain.getChecks()) {
			if (!CommonUtils.isEmpty(check.getName())) {
				builder.space()._add("CONSTRAINT").space().name(check.getName());
			}
			builder.space()._add("CHECK (")._add(check.getExpression())._add(")");
			addDeferrability(builder, check.getDeferrability());
			String rely = check.getSpecifics().get(RELY);
			if (rely != null) {
				if (!"RELY".equals(rely) && !"NORELY".equals(rely)) {
					throw new IllegalArgumentException("Invalid domain check RELY: " + check.getName());
				}
				builder.space()._add(rely);
			}
			builder.space()._add(check.isEnable() ? "ENABLE" : "DISABLE");
			String validated = check.getSpecifics().get(VALIDATED);
			if (validated != null) {
				if (!"VALIDATED".equals(validated) && !"NOT VALIDATED".equals(validated)) {
					throw new IllegalArgumentException("Invalid domain check VALIDATED: " + check.getName());
				}
				builder.space()._add("VALIDATED".equals(validated) ? "VALIDATE" : "NOVALIDATE");
			}
		}
	}

	private void addDeferrability(OracleSqlBuilder builder, Deferrability value) {
		if (value == null) return;
		if (value != Deferrability.NotDeferrable) builder.space()._add("DEFERRABLE");
		builder.space()._add(value.getSqlValue());
	}

	private void addExpression(final Domain domain, final OracleSqlBuilder builder, final String key) {
		final String expression = CommonUtils.trim(domain.getSpecifics().get(key));
		if (expression != null) {
			builder.space()._add(key).space()._add(expression);
		}
	}

	private void validate(final Domain domain) {
		if (OracleDomainAssociationUtils.isFlexible(domain)) {
			throw new IllegalArgumentException("Flexible domain selector requires a complete CREATE DOMAIN definition: " + domain.getName());
		}
		for (var column : domain.getColumns()) {
			if (column.getDataType() == DataType.ENUM || column.getArrayDimension() != 0
					|| column.getArrayDimensionUpperBound() != 0) {
				throw new IllegalArgumentException("Use a complete CREATE DOMAIN definition for unsupported domain column type: "
						+ domain.getName() + "." + column.getName());
			}
		}
		if (domain.getDataType() == DataType.ENUM) {
			throw new IllegalArgumentException("Use a complete CREATE DOMAIN definition for Oracle enumerated domains: " + domain.getName());
		}
		if (domain.getColumns().isEmpty() && domain.getDataType() == null && domain.getDataTypeName() == null) {
			throw new IllegalArgumentException("Oracle data use case domain requires a data type: " + domain.getName());
		}
		if (domain.getArrayDimension() != 0 || domain.getArrayDimensionUpperBound() != 0) {
			throw new IllegalArgumentException(
					"Oracle single-column data use case domain cannot be an array: " + domain.getName());
		}
		validateExpression(domain, DISPLAY);
		validateExpression(domain, ORDER);
	}

	private void validateExpression(final Domain domain, final String key) {
		final String expression = domain.getSpecifics().get(key);
		if (expression != null && (expression.indexOf('\r') >= 0 || expression.indexOf('\n') >= 0)) {
			throw new IllegalArgumentException(key + " must not contain line breaks: " + domain.getName());
		}
	}
}
