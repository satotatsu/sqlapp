/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.List;
import java.util.Collections;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions;
import com.sqlapp.data.db.sql.SimpleAlterSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.data.schemas.State;

/** SQL Server 2005+ explicit NEXT USED assignment and removal. */
public class SqlServer2005AlterPartitionSchemeFactory
		extends SimpleAlterSqlFactory<PartitionScheme, SqlServerSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(PartitionScheme scheme) {
		String value = SqlServerPartitionOptions.getNextUsed(scheme);
		if (value == null)
			throw new IllegalArgumentException("Specify NEXT_USED or use clearNextUsed for ALTER PARTITION SCHEME");
		SqlServerSqlBuilder builder = newSqlBuilder(getDialect());
		builder.alter().partition().scheme().space().name(scheme)._add(" NEXT USED");
		if (!value.isEmpty())
			builder._add(" [")._add(value.replace("]", "]]"))._add("]");
		return List.of(createOperation(builder.toString(), SqlType.ALTER, scheme));
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!difference.getState().isChanged())
			return Collections.emptyList();
		if (difference.getState() == State.Added)
			return getSqlFactoryRegistry().createSql(difference.getTarget(PartitionScheme.class), SqlType.CREATE);
		if (difference.getState() == State.Deleted)
			return getSqlFactoryRegistry().createSql(difference.getOriginal(PartitionScheme.class), SqlType.DROP);
		PartitionScheme original = difference.getOriginal(PartitionScheme.class),
				target = difference.getTarget(PartitionScheme.class);
		String previous = SqlServerPartitionOptions.getNextUsed(original),
				next = SqlServerPartitionOptions.getNextUsed(target);
		if (previous != null && next == null)
			throw new IllegalArgumentException("Use clearNextUsed to explicitly remove NEXT USED");
		PartitionScheme normalized = target.clone();
		if (original.getSpecifics().containsKey(SqlServerPartitionOptions.NEXT_USED))
			normalized.getSpecifics().put(SqlServerPartitionOptions.NEXT_USED,
					original.getSpecifics().get(SqlServerPartitionOptions.NEXT_USED));
		else
			normalized.getSpecifics().remove(SqlServerPartitionOptions.NEXT_USED);
		if (!normalized.equals(original))
			throw new IllegalArgumentException(
					"ALTER PARTITION SCHEME supports NEXT USED changes only; migrate existing partition placement explicitly");
		return java.util.Objects.equals(previous, next) ? Collections.emptyList() : createSql(target);
	}
}
