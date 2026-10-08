/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;


import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

/**
 * Foreign Key Constraint生成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<PostgresSqlBuilder> {

	@Override
	protected void addMatchOption(ForeignKeyConstraint obj, PostgresSqlBuilder builder) {
		if (obj.getMatchOption() != null) {
			builder.matchOption(obj);
		}
	}
	@Override
	protected void addCascadeRule(ForeignKeyConstraint obj, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendCascadeRules(obj, builder);
	}

	@Override
	protected void addDeferrability(ForeignKeyConstraint obj, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendDeferrability(obj, builder);
	}

	protected boolean supportsNotValid() { return false; }

	@Override
	protected void addAfter(ForeignKeyConstraint constraint, PostgresSqlBuilder builder) {
		PostgresConstraintOptions.appendNotValid(constraint, builder, supportsNotValid());
	}


	@Override
	public List<SqlOperation> createSql(ForeignKeyConstraint constraint) {
		List<SqlOperation> result = super.createSql(constraint);
		if (!result.isEmpty() && constraint.getRemarks() != null) {
			var builder = createSqlBuilder();
			PostgresConstraintOptions.constraintComment(constraint, constraint.getTable(), builder,
					getOptions().isDecorateSchemaName());
			addSql(result, builder, SqlType.SET_COMMENT, constraint);
		}
		return result;
	}
}
