package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Schema;

/** Informix object owners are represented as Schema objects. */
public class InformixCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected void addCreateSchema(Schema schema, List<SqlOperation> operations) {
		// Informix CREATE SCHEMA AUTHORIZATION is a DB-Access block, not an
		// owner-namespace declaration.
	}
}
