/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Catalog;

/** Orders cross-schema domain additions and removals around their prerequisites and consumers. */
public class Oracle23aiAlterCatalogFactory extends AlterCatalogFactory {
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var operations = super.createDiffSql(difference);
		OracleDomainDependencies.validateRemovals(difference.getTarget(Catalog.class).getSchemas().stream().toList(), operations);
		if (!getOptions().isDecorateSchemaName() && operations.stream().anyMatch(o -> (o.getSqlType() == SqlType.DROP || o.getSqlType() == SqlType.CREATE) && o.getOriginal() instanceof Domain)) {
			throw new IllegalArgumentException("Catalog domain changes require decorateSchemaName=true to preserve cross-schema identity");
		}
		return OracleDomainDependencies.postponeDrops(OracleDomainMigrationOrder.additions(operations));
	}
}
