package com.sqlapp.data.db.dialect.virtica.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

/**
 * A complete vendor schema definition owns its dependency order and child
 * CREATEs.
 */
public class VirticaCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	public List<SqlOperation> createSql(Schema schema) {
		if (schema.getDefinition() == null || schema.getDefinition().isEmpty())
			return super.createSql(schema);
		var result = new ArrayList<SqlOperation>();
		for (String statement : com.sqlapp.data.db.dialect.virtica.metadata.VirticaSchemaDefinitionValidator
				.statements(getDialect(), schema))
			result.add(createOperation(statement, SqlType.CREATE, schema));
		if (result.isEmpty())
			throw new IllegalArgumentException(
					"Vertica schema definition has no executable statements: " + schema.getName());
		return result;
	}
}
