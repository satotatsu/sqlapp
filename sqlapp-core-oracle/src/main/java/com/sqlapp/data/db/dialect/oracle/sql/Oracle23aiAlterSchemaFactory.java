/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;

/**
 * Removes domains after table detachments and other dependent-object changes.
 */
public class Oracle23aiAlterSchemaFactory extends AlterSchemaFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var operations = super.createDiffSql(difference);
		OracleDomainDependencies.validateRemovals(List.of(difference.getTarget(Schema.class)), operations);
		return OracleDomainDependencies.postponeDrops(operations);
	}
}
