/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE factory for Oracle Database 23ai data use case domains.
 */
public class Oracle23aiCreateDomainFactory extends OracleCreateDomainFactory {

	public static final String STRICT = "STRICT";
	public static final String DEFAULT_ON_NULL = "DEFAULT_ON_NULL";
	public static final String CONSTRAINT_NAME = "CONSTRAINT_NAME";
	public static final String DISPLAY = "DISPLAY";
	public static final String ORDER = "ORDER";

	@Override
	protected void addCreateObject(final Domain domain,
			final OracleSqlBuilder builder) {
		validate(domain);
		builder.create().space()._add("DOMAIN").space();
		builder.ifNotExists(this.getOptions().isCreateIfNotExists()).space();
		builder.name(domain, this.getOptions().isDecorateSchemaName());
		builder.space().as().space();
		builder.typeDefinition(domain.getDataType(), domain.getDataTypeName(),
				domain.getLength(), domain.getScale());
		if (Boolean.TRUE.equals(
				domain.getSpecifics().get(STRICT, Boolean.class))) {
			builder.space()._add("STRICT");
		}
		if (domain.getDefaultValue() != null) {
			builder.space()._add("DEFAULT");
			if (Boolean.TRUE.equals(domain.getSpecifics()
					.get(DEFAULT_ON_NULL, Boolean.class))) {
				builder.space()._add("ON NULL");
			}
			builder.space()._add(domain.getDefaultValue());
		}
		if (domain.isNotNull()) {
			builder.space()._add("NOT NULL");
		}
		if (domain.getCheck() != null) {
			builder.space()._add("CONSTRAINT");
			final String constraintName = CommonUtils.trim(
					domain.getSpecifics().get(CONSTRAINT_NAME));
			if (constraintName != null) {
				builder.space().name(constraintName);
			}
			builder.space()._add("CHECK").space()._add("(")
					._add(domain.getCheck())._add(")");
			if (domain.getDeferrability() != null) {
				builder.space()._add(domain.getDeferrability().getSqlValue());
			}
		}
		addExpression(domain, builder, DISPLAY);
		addExpression(domain, builder, ORDER);
		OracleAnnotationUtils.addAnnotations(builder, domain);
	}

	private void addExpression(final Domain domain,
			final OracleSqlBuilder builder, final String key) {
		final String expression = CommonUtils.trim(
				domain.getSpecifics().get(key));
		if (expression != null) {
			builder.space()._add(key).space()._add(expression);
		}
	}

	private void validate(final Domain domain) {
		if (domain.getDataType() == null && domain.getDataTypeName() == null) {
			throw new IllegalArgumentException(
					"Oracle data use case domain requires a data type: "
							+ domain.getName());
		}
		if (domain.getArrayDimension() != 0
				|| domain.getArrayDimensionUpperBound() != 0) {
			throw new IllegalArgumentException(
					"Oracle single-column data use case domain cannot be an array: "
							+ domain.getName());
		}
		validateExpression(domain, DISPLAY);
		validateExpression(domain, ORDER);
	}

	private void validateExpression(final Domain domain, final String key) {
		final String expression = domain.getSpecifics().get(key);
		if (expression != null
				&& (expression.indexOf('\r') >= 0
						|| expression.indexOf('\n') >= 0)) {
			throw new IllegalArgumentException(key
					+ " must not contain line breaks: " + domain.getName());
		}
	}
}
