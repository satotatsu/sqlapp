/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.State;

/**
 * Native ALTER with explicit rejection of ambiguous destructive column changes.
 */
public class H2AlterTableFactory extends AbstractAlterTableFactory<H2SqlBuilder> {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var changes = difference.toDifference().getChangedProperties(getDialect());
		var columns = (DbObjectDifferenceCollection) changes.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (columns != null && !columns.getList(State.Deleted).isEmpty() && !columns.getList(State.Added).isEmpty())
			throw new IllegalArgumentException(
					"H2 cannot distinguish a column rename with type changes from DROP/ADD; rename the column separately before other changes");
		return super.createDiffSql(difference);
	}
}
