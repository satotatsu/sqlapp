/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateDomainFactory;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.util.CommonUtils;

/**
 * Creates an H2 domain.
 */
public class H2CreateDomainFactory extends AbstractCreateDomainFactory<H2SqlBuilder> {
	@Override
	protected boolean supportsDomainChecks() {
		return true;
	}

	@Override
	protected boolean supportsDomainDefinition() {
		return true;
	}

	@Override
	protected void addCreateObject(final Domain obj, final H2SqlBuilder builder) {
		if (!obj.getDefinition().isEmpty()) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().domain().ifNotExists(this.getOptions().isCreateIfNotExists())
				.name(obj, this.getOptions().isDecorateSchemaName()).as().space();
		String parent = obj.getSpecifics().get("h2.parentDomainName");
		if (parent != null) {
			String schema = obj.getSpecifics().get("h2.parentDomainSchema");
			if (parent.isBlank() || schema == null || schema.isBlank())
				throw new IllegalArgumentException("H2 parent domain requires schema and name");
			builder.name(schema)._add(".").name(parent);
		} else {
			var type = new com.sqlapp.data.schemas.Column().setDataTypeName(obj.getDataTypeName())
					.setDataType(obj.getDataType()).setLength(obj.getLength()).setScale(obj.getScale());
			type.getValues().clear();
			type.getValues().addAll(obj.getValues());
			builder.addTypeDefinition(type);
		}
		if (!CommonUtils.isEmpty(obj.getDefaultValue())) {
			builder.default_().space()._add(obj.getDefaultValue());
		}
		if (!CommonUtils.isEmpty(obj.getOnUpdate())) {
			builder.on().update().space()._add(obj.getOnUpdate());
		}
		if (!obj.getChecks().isEmpty()) {
			for (var check : obj.getChecks()) {
				if (check.getExpression() == null || check.getExpression().isBlank() || !check.isEnable()
						|| check.getDeferrability() != null)
					throw new IllegalArgumentException("H2 domain checks require an enabled, nondeferred expression");
				if (check.getName() != null)
					builder.constraint().name(check.getName());
				builder.check().space()._add("(")._add(check.getExpression())._add(")");
			}
		} else if (!CommonUtils.isEmpty(obj.getCheck())) {
			builder.check().space()._add('(')._add(obj.getCheck())._add(')');
		}
	}
}
