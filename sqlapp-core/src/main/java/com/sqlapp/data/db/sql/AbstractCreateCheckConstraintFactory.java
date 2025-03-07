/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * CHECK CONSTRAINT生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateCheckConstraintFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<CheckConstraint, S> implements AddTableObjectDetailFactory<CheckConstraint, S>{

	@Override
	public List<SqlOperation> createSql(final CheckConstraint obj) {
		List<SqlOperation> sqlList = list();
		S builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		return sqlList;
	}


	@Override
	public void addCreateObject(final CheckConstraint obj, S builder) {
		builder.alter().table().name(obj.getTable(), this.getOptions().isDecorateSchemaName());
		builder.add();
		addObjectDetail(obj, obj.getTable(), builder);
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param obj
	 * @param table
	 * @param builder
	 */
	@Override
	public void addObjectDetail(final CheckConstraint obj, Table table, S builder) {
		builder.constraint().space();
		if (table!=null){
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		} else{
			builder.name(obj, false);
		}
		builder.check();
		addCheckConstraintOption(obj, builder);
		builder.space()._add('(');
		builder._add(obj.getExpression());
		builder.space()._add(')');
		addDeferrability(obj, builder);
		addCheckConstraintAfter(obj, builder);
	}
	
	protected void addDeferrability(CheckConstraint constraint, S builder) {
		if (constraint.getDeferrability() == null) {
			return;
		}
		//builder.space().append(constraint.getDeferrability());
	}
	
	/**
	 * Check制約のオプション設定用のメソッドです
	 * 
	 * @param constraint
	 * @param builder
	 */
	protected void addCheckConstraintOption(CheckConstraint constraint,
			S builder) {

	}
	
	protected void addCheckConstraintAfter(CheckConstraint constraint, S builder) {

	}

}
