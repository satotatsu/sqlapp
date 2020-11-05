/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * CHECK CONSTRAINT生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateForeignKeyConstraintFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<ForeignKeyConstraint, S> 
	implements AddTableObjectDetailFactory<ForeignKeyConstraint, S>{

	@Override
	public List<SqlOperation> createSql(final ForeignKeyConstraint obj) {
		List<SqlOperation> sqlList = list();
		S builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		return sqlList;
	}


	@Override
	public void addCreateObject(final ForeignKeyConstraint obj, S builder) {
		builder.alter().table().name(obj.getTable(), this.getOptions().isDecorateSchemaName());
		builder.add();
		addObjectDetail(obj, obj.getParent()!=null?obj.getParent().getTable():null, builder);
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param obj
	 * @param table
	 * @param builder
	 */
	@Override
	public void addObjectDetail(final ForeignKeyConstraint obj, Table table, S builder) {
		builder.constraint().space();
		if (table!=null){
			builder.name(obj, false);
		} else{
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		}
		builder.space().foreignKey();
		addOption(obj, builder);
		builder.space()._add("(");
		builder.names(obj.getColumns());
		builder.space()._add(")");
		builder.references();
		if (obj.getTable().getSchemaName()!=null&&obj.getRelatedTable().getSchemaName()!=null
				&&!CommonUtils.eq(obj.getTable().getSchemaName(), obj.getRelatedTable().getSchemaName())){
			builder.name(obj.getRelatedTable(), true);
		} else{
			builder.name(obj.getRelatedTable(), false);
		}
		builder.space()._add('(');
		builder.names(obj.getRelatedColumns());
		builder.space()._add(')');
		addMatchOption(obj, builder);
		addCascadeRule(obj, builder);
		addDeferrability(obj, builder);
		addAfter(obj, builder);
	}
	
	protected void addMatchOption(ForeignKeyConstraint obj, S builder) {
	}
	
	protected void addCascadeRule(ForeignKeyConstraint obj, S builder) {
		builder.cascadeRule(obj);
	}
	
	protected void addDeferrability(ForeignKeyConstraint obj, S builder) {
		//builder.space().append(obj.getDeferrability());
	}

	/**
	 * Check制約のオプション設定用のメソッドです
	 * 
	 * @param constraint
	 * @param builder
	 */
	protected void addOption(ForeignKeyConstraint constraint,
			S builder) {

	}
	
	protected void addAfter(ForeignKeyConstraint constraint, S builder) {

	}

}
