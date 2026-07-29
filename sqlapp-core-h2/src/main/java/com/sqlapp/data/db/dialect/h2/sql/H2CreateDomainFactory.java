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
public class H2CreateDomainFactory
		extends AbstractCreateDomainFactory<H2SqlBuilder> {

	@Override
	protected void addCreateObject(final Domain obj,
			final H2SqlBuilder builder) {
		builder.create().domain()
				.ifNotExists(this.getOptions().isCreateIfNotExists())
				.name(obj, this.getOptions().isDecorateSchemaName())
				.as().space()
				.typeDefinition(obj.getDataType(), obj.getDataTypeName(),
						obj.getLength(), obj.getScale());
		if (!CommonUtils.isEmpty(obj.getDefaultValue())) {
			builder.default_().space()._add(obj.getDefaultValue());
		}
		if (!CommonUtils.isEmpty(obj.getOnUpdate())) {
			builder.on().update().space()._add(obj.getOnUpdate());
		}
		builder.notNull(obj.isNotNull());
		if (!CommonUtils.isEmpty(obj.getCheck())) {
			builder.check().space()._add('(')
					._add(obj.getCheck())._add(')');
		}
	}
}
