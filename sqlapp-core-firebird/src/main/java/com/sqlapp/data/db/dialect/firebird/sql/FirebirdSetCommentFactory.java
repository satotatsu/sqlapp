/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Firebird COMMENT ON statements for independently named schema objects. */
public class FirebirdSetCommentFactory extends SimpleSqlFactory<AbstractNamedObject<?>, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(AbstractNamedObject<?> object) {
		if (getDialect().compareTo(DialectHolder.defaultDialect20) < 0)
			throw new IllegalArgumentException("COMMENT ON requires Firebird 2.0 or later");
		String kind;
		if (object instanceof View)
			kind = "VIEW";
		else if (object instanceof Table)
			kind = "TABLE";
		else if (object instanceof Column)
			kind = "COLUMN";
		else if (object instanceof Domain)
			kind = "DOMAIN";
		else if (object instanceof Index)
			kind = "INDEX";
		else if (object instanceof Trigger)
			kind = "TRIGGER";
		else if (object instanceof Sequence)
			kind = "GENERATOR";
		else if (object instanceof Procedure)
			kind = "PROCEDURE";
		else if (object instanceof Function function)
			kind = FirebirdExternalFunctionSql.legacy(function, getDialect()) ? "EXTERNAL FUNCTION" : "FUNCTION";
		else if (object instanceof com.sqlapp.data.schemas.Package)
			kind = "PACKAGE";
		else if (object instanceof Role)
			kind = "ROLE";
		else
			throw new IllegalArgumentException(
					"Unsupported Firebird comment target: " + object.getClass().getSimpleName());
		if (("FUNCTION".equals(kind) || "PACKAGE".equals(kind))
				&& getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
			throw new IllegalArgumentException(kind + " comments require Firebird 3.0 or later");
		var builder = createSqlBuilder()._add("COMMENT ON " + kind + " ");
		if (object instanceof Column column) {
			String relation = column.getTableName();
			if (relation == null && column.getParent() != null
					&& column.getParent().getParent() instanceof Table parent)
				relation = parent.getName();
			if (relation == null)
				throw new IllegalArgumentException("Column comment requires a table or view name");
			builder.name(relation)._add(".");
		}
		builder.name(object.getName())._add(" IS ");
		if (object.getRemarks() == null)
			builder._add("NULL");
		else
			builder.sqlChar(object.getRemarks());
		List<SqlOperation> result = new java.util.ArrayList<>();
		addSql(result, builder, SqlType.SET_COMMENT, object);
		return result;
	}
}
