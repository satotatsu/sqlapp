/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

/** Explicit native comments, including removal with a null remarks value. */
public class H2SetCommentFactory extends AbstractSqlFactory<AbstractNamedObject<?>, H2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(AbstractNamedObject<?> object) {
		String kind;
		if (object instanceof Column)
			kind = "COLUMN";
		else if (object instanceof View)
			kind = "VIEW";
		else if (object instanceof Table)
			kind = "TABLE";
		else if (object instanceof Function)
			kind = "ALIAS";
		else if (object instanceof Domain)
			kind = "DOMAIN";
		else if (object instanceof Sequence)
			kind = "SEQUENCE";
		else if (object instanceof Index)
			kind = "INDEX";
		else if (object instanceof Trigger)
			kind = "TRIGGER";
		else if (object instanceof Constant)
			kind = "CONSTANT";
		else if (object instanceof Schema)
			kind = "SCHEMA";
		else
			throw new IllegalArgumentException(
					"H2 comments are not supported for " + object.getClass().getSimpleName());
		requireName(object.getName());
		var builder = createSqlBuilder(getDialect());
		builder.comment().on().space()._add(kind).space();
		if (object instanceof Column column) {
			requireName(column.getTableName());
			requireName(column.getSchemaName());
			if (getOptions().isDecorateSchemaName())
				builder.name(column.getSchemaName())._add(".");
			builder.name(column.getTableName())._add(".").name(column.getName());
		} else if (object instanceof AbstractSchemaObject<?> named) {
			requireName(named.getSchemaName());
			builder.name(named, getOptions().isDecorateSchemaName());
		} else
			builder.name(object.getName());
		builder.space()._add("IS ");
		if (object.getRemarks() == null)
			builder._add("NULL");
		else
			builder.sqlChar(object.getRemarks());
		return List.of(new SqlOperation(builder.toString(), SqlType.SET_COMMENT, object));
	}

	private static void requireName(String name) {
		if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
			throw new IllegalArgumentException("H2 COMMENT requires explicit nonempty object identifiers without NUL");
	}
}
