package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.CreateCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/** Emits routine grants after the Catalog's owners, roles and routines. */
public class InformixCreateCatalogFactory extends CreateCatalogFactory {
	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		var operations = super.createSql(catalog);
		List<SqlOperation> grants = new java.util.ArrayList<>();
		for (var operation : operations)
			if (InformixGrantOrder.isObjectGrant(operation))
				grants.add(operation);
		operations.removeIf(InformixGrantOrder::isObjectGrant);
		addCreateDbObjectCollection(SchemaObjectProperties.ROUTINE_PRIVILEGES.getLabel(),
				catalog.getRoutinePrivileges(), grants);
		operations.addAll(InformixGrantOrder.order(grants));
		return operations;
	}
}
