/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

/**
 * Foreign Key Constraint生成クラス
 * 
 * @author satoh
 * 
 */
public class OracleCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<OracleSqlBuilder> {

	@Override
	protected void addMatchOption(ForeignKeyConstraint obj, OracleSqlBuilder builder) {
	}
	@Override protected void addDeferrability(ForeignKeyConstraint constraint, OracleSqlBuilder builder) {
		OracleConstraintState.deferrability(constraint, builder);
	}
	@Override protected void addAfter(ForeignKeyConstraint constraint, OracleSqlBuilder builder) {
		OracleConstraintState.after(constraint, builder);
	}
	@Override protected void addCascadeRule(ForeignKeyConstraint constraint, OracleSqlBuilder builder) {
		var update = constraint.getUpdateRule();
		var delete = constraint.getDeleteRule();
		if (update != null && update != com.sqlapp.data.schemas.CascadeRule.None) {
			throw new IllegalArgumentException("Oracle does not support ON UPDATE actions: " + constraint.getName());
		}
		if (delete == com.sqlapp.data.schemas.CascadeRule.SetDefault || delete == com.sqlapp.data.schemas.CascadeRule.Restrict) {
			throw new IllegalArgumentException("Unsupported Oracle ON DELETE action: " + constraint.getName());
		}
		if (delete == com.sqlapp.data.schemas.CascadeRule.Cascade || delete == com.sqlapp.data.schemas.CascadeRule.SetNull) {
			builder.space()._add("ON DELETE ")._add(delete.getSqlValue());
		}
	}
}
