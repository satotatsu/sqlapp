/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
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
import com.sqlapp.data.schemas.State;

/**
 * MySQLスキーマ変更コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterSchemaFactory extends
		AbstractAlterSchemaFactory<MySqlSqlBuilder> {

	@Override
	protected void addAlterSchema(DbObjectDifference difference,
			Map<String, Difference<?>> allDiff,
			DbObjectDifference characterSetDiff,
			DbObjectDifference collationDiff,
			DbObjectDifference characterSemanticsDiff, List<SqlOperation> sqlList) {
		Schema orgSchema=difference.getOriginal(Schema.class);
		Schema schema=difference.getTarget(Schema.class);
		MySqlSqlBuilder builder = createSqlBuilder(getDialect());
		if (difference.getState() == State.Modified) {
			if (characterSetDiff != null || collationDiff != null)
				builder.alter().schema()
						.name((Schema) difference.getOriginal());
			if (characterSetDiff != null) {
				builder.characterSet().space()
						._add(characterSetDiff.getTarget().toString());
			}
			if (collationDiff != null) {
				builder.collate().space()._add(collationDiff.getTarget().toString());
			}
			add(sqlList, this.createOperation(builder.toString(), SqlType.ALTER, orgSchema,schema));
		}
	}
}
