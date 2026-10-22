/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.CommonUtils;

/** Creates a HANA table type from the existing shared Type/TypeColumn model. */
public class SapHanaCreateTypeFactory extends AbstractCreateNamedObjectFactory<Type, SapHanaSqlBuilder> {
	@Override protected void addCreateObject(final Type type, final SapHanaSqlBuilder builder) {
		if (!CommonUtils.isEmpty(type.getDefinition())) {
			builder._add(type.getDefinition());
			return;
		}
		if (type.getColumns().isEmpty()) {
			throw new IllegalArgumentException("SAP HANA table type requires columns: " + type.getName());
		}
		builder.create().space()._add("TYPE").name(type, getOptions().isDecorateSchemaName()).as().space()._add("TABLE (");
		int position = 0;
		for (var column : type.getColumns()) {
			builder.comma(position++ > 0).name(column.getName()).space().addTypeDefinition(new Column(column.getName())
					.setDataType(column.getDataType()).setDataTypeName(column.getDataTypeName())
					.setLength(column.getLength()).setScale(column.getScale()));
		}
		builder._add(")");
	}
}
