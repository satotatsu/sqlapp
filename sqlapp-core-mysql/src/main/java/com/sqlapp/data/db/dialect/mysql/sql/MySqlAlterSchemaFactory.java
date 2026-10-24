/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.State;

/**
 * MySQLスキーマ変更コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterSchemaFactory extends AbstractAlterSchemaFactory<MySqlSqlBuilder> {

	@Override
	protected void addAlterSchema(DbObjectDifference difference, Map<String, Difference<?>> allDiff,
			List<SqlOperation> sqlList) {
		if (difference.getState() != State.Modified) {
			return;
		}
		if (allDiff.containsKey(SchemaProperties.NAME.getLabel())) {
			throw new IllegalArgumentException("MySQL does not support renaming a database with ALTER SCHEMA");
		}
		boolean charsetChanged = allDiff.containsKey(SchemaProperties.CHARACTER_SET.getLabel());
		boolean collationChanged = allDiff.containsKey(SchemaProperties.COLLATION.getLabel());
		if (!charsetChanged && !collationChanged) {
			return;
		}
		Schema original = difference.getOriginal(Schema.class);
		Schema target = difference.getTarget(Schema.class);
		if ((charsetChanged && com.sqlapp.util.CommonUtils.isEmpty(target.getCharacterSet()))
				|| (collationChanged && com.sqlapp.util.CommonUtils.isEmpty(target.getCollation()))) {
			throw new IllegalArgumentException(
					"Specify an explicit target database character set or collation; removing it has no portable default");
		}
		MySqlSqlBuilder builder = createSqlBuilder(getDialect());
		builder.alter().schema().name(original);
		MySqlCreateSchemaFactory.appendDefaults(target, builder);
		add(sqlList, createOperation(builder.toString(), SqlType.ALTER, original, target));
	}

	@Override
	protected void addAlterSchema(DbObjectDifference difference, Map<String, Difference<?>> allDiff,
			DbObjectDifference characterSetDiff, DbObjectDifference collationDiff,
			DbObjectDifference characterSemanticsDiff, List<SqlOperation> sqlList) {
		addAlterSchema(difference, allDiff, sqlList);
	}
}
