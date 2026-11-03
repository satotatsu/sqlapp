/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;
import com.sqlapp.data.db.dialect.db2.sql.Db2CreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;

/**
 * Guard the basic modeled path from silently losing advanced table attributes.
 */
public class Db2HostCreateTableFactory extends Db2CreateTableFactory {
	@Override
	public List<SqlOperation> createSql(Table table) {
		if (getOptions().isCreateIfNotExists())
			throw new IllegalArgumentException("IF NOT EXISTS is outside basic Db2 host CREATE support");
		if (!table.getDefinition().isEmpty() || table.getPartitioning() != null || table.getTableSpaceName() != null
				|| table.getIndexTableSpaceName() != null || table.getLobTableSpaceName() != null
				|| !table.getIndexes().isEmpty() || table.getCollation() != null
				|| com.sqlapp.data.db.dialect.db2.Db2Nickname.isNickname(table))
			throw new IllegalArgumentException(
					"Basic Db2 host CREATE supports modeled columns and keys only: " + table.getName());
		for (var constraint : table.getConstraints()) {
			if (!constraint.isEnable())
				throw new IllegalArgumentException(
						"Non-enforced constraints require extended Db2 host support: " + constraint.getName());
		}
		return super.createSql(table);
	}
}
