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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * 1個のオブジェクトを指定してDB上のオブジェクトを操作する抽象クラス
 * 
 * @author satoh
 * 
 * @param <T>
 */
public abstract class SimpleSqlFactory<T extends DbCommonObject<?>, S extends AbstractSqlBuilder<?>>
		extends AbstractSqlFactory<T, S> {

	protected S createSqlBuilder() {
		return newSqlBuilder(this.getDialect());
	}

	@Override
	@SuppressWarnings("unchecked")
	protected S newSqlBuilder(final Dialect dialect){
		return (S)dialect.createSqlBuilder();
	}
	
	@SuppressWarnings("unchecked")
	protected <X extends AbstractDbObject<?>,Y extends AbstractSqlBuilder<?>> AddObjectDetail<X, Y> getAddObjectDetail(final X obj, final SqlType sqlType){
		final SqlFactory<T> factory = getSqlFactoryRegistry()
				.getSqlFactory((T)obj, sqlType);
		if (factory instanceof AddObjectDetail<?,?>) {
			return (AddObjectDetail<X, Y>)factory;
		}
		return null;

	}
	
	protected boolean isInsertable(final Column column) {
		if (!this.getOptions().getTableOptions().getInsertableColumn().test(column)) {
			return false;
		}
		return true;
	}

	protected boolean isUpdateable(final Column column) {
		if (!this.getOptions().getTableOptions().getUpdateableColumn().test(column)) {
			return false;
		}
		if (this.getOptions().getTableOptions().getCreatedAtColumn().test(column)) {
			return false;
		}
		return true;
	}
	
	
	protected void addSelectColumnComment(final Column column, final S builder) {
		final String comment = this.getOptions().getTableOptions().getSelectColumnComment().apply(column);
		if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
			builder.space().addComment(comment);
		}
	}

	protected void addInsertColumnComment(final Column column, final S builder) {
		final String comment = this.getOptions().getTableOptions().getUpdateColumnComment().apply(column);
		if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
			builder.space().addComment(comment);
		}
	}

	protected void addUpdateColumnComment(final Column column, final S builder) {
		final String comment = this.getOptions().getTableOptions().getUpdateColumnComment().apply(column);
		if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
			builder.space().addComment(comment);
		}
	}

	protected void addWhereColumnComment(final Column column, final S builder) {
		final String comment = this.getOptions().getTableOptions().getWhereColumnComment().apply(column);
		if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
			builder.space().addComment(comment);
		}
	}

	protected void addTableComment(final Table table, final S builder) {
		final String comment = this.getOptions().getTableOptions().getTableComment().apply(table);
		if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, table.getName())) {
			builder.space().addComment(comment);
		}
	}
}
