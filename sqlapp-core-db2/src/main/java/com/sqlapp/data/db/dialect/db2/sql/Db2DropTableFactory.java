/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.sql.DropTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

public class Db2DropTableFactory extends DropTableFactory {
	@Override
	protected void addDropObject(Table table, AbstractSqlBuilder<?> builder) {
		if (Db2Nickname.isNickname(table)) {
			Db2Nickname.get(table);
			builder._add("DROP NICKNAME " + Db2NicknameSql.name(table));
		} else
			super.addDropObject(table, builder);
	}
}
