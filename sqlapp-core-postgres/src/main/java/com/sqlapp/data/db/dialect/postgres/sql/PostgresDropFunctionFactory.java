package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropFunctionFactory;
import com.sqlapp.data.schemas.Function;

/** Drops the exact input signature, including definition-backed procedures. */
public class PostgresDropFunctionFactory extends AbstractDropFunctionFactory<PostgresSqlBuilder> {
	@Override
	protected void addDropObject(Function function, PostgresSqlBuilder builder) {
		builder.drop();
		if ("PROCEDURE".equals(function.getSpecifics().get("ROUTINE_KIND")))
			builder.procedure();
		else
			builder.function();
		builder.space().specificName(function, getOptions().isDecorateSchemaName());
	}
}
