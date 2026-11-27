package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Procedure;

/**
 * Drops the exact stored procedure overload using modeled native argument
 * types.
 */
public class VerticaDropProcedureFactory extends AbstractDropNamedObjectFactory<Procedure, VerticaSqlBuilder> {
	@Override
	protected void addDropObject(Procedure procedure, VerticaSqlBuilder builder) {
		builder.drop().procedure().ifExists(getOptions().isDropIfExists());
		builder.name(procedure, getOptions().isDecorateSchemaName())._add('(');
		boolean first = true;
		for (var argument : procedure.getArguments()) {
			if (argument.getDataType() == null || argument.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER
					|| argument.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN)
				throw new IllegalArgumentException(
						"Vertica procedure DROP requires resolved argument types: " + procedure.getName());
			if (!first)
				builder.comma().space();
			var type = getDialect().createSqlBuilder();
			type.typeDefinition(argument);
			builder._add(type.toString().replaceAll("\\([^()]*\\)", ""));
			first = false;
		}
		builder._add(')');
	}
}
