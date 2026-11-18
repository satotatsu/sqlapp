/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.sql.InsertFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.db.dialect.vertica.VerticaIntervalJdbcTypeHandler;

/** Array INSERT parameters need an explicit CAST to avoid the scalar-only JDBC COPY batch path. */
public class VerticaInsertFactory extends InsertFactory {
	@Override protected String getColumnParameterExpression(Column column, String defaultValue) {
		String expression=super.getColumnParameterExpression(column,defaultValue);
		if(column.getArrayDimension()==0 || column.getDataType().getMetaTtype()!=MetaType.INTERVAL) return expression;
		return "CAST("+expression+" AS "+new VerticaIntervalJdbcTypeHandler(column.getDataType()).arrayType(column)+")";
	}
}
