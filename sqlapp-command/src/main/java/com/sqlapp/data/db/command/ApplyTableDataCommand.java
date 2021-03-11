/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command;

import java.sql.Connection;
import java.util.List;
import java.util.function.Predicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectFilter;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableCollection;
import com.sqlapp.util.CommonUtils;

/**
 * データ適用コマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class ApplyTableDataCommand extends
		AbstractFile2DataSourceCommand<Table> {

	private SqlType sqlType = SqlType.MERGE_BY_PK;

	/**
	 * 処理対象から除くオブジェクト
	 */
	private Predicate<AbstractSchemaObject<?>> filter = new SchemaObjectFilter();

	@Override
	protected List<Table> getTarget(final List<DbCommonObject<?>> totalObjects,
			final Connection connection, final Dialect dialect) {
		final List<Table> target = CommonUtils.list();
		for (final DbCommonObject<?> obj : totalObjects) {
			if (obj instanceof Catalog) {
				final Catalog catalog = (Catalog) obj;
				for (final Schema schema : catalog.getSchemas()) {
					for (final Table table : schema.getTables()) {
						target.add(table);
					}
				}
			} else if (obj instanceof Schema) {
				final Schema schema = (Schema) obj;
				for (final Table table : schema.getTables()) {
					target.add(table);
				}
			} else if (obj instanceof TableCollection) {
				final TableCollection tables = (TableCollection) obj;
				target.addAll(tables);
			} else if (obj instanceof Table) {
				final Table table = (Table) obj;
				target.add(table);
			}
		}
		return target;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.command.AbstractFile2DataSourceCommand#filter(java
	 * .util.List)
	 */
	@Override
	protected List<Table> filter(final List<Table> list) {
		final List<Table> target = CommonUtils.list();
		for (final Table obj : list) {
			if (filter.test(obj)) {
				target.add(obj);
			}
		}
		return target;
	}

	@Override
	protected List<Table> sort(List<Table> list) {
		if (this.getSqlType().getTableComparator()!=null){
			list = SchemaUtils.getNewSortedTableList(list, this.getSqlType().getTableComparator());
		}
		return list;
	}

	@Override
	protected void handle(final Table obj,
			final SqlFactoryRegistry sqlFactoryRegistry, final Connection connection, final Dialect dialect) throws Exception {
		final SqlFactory<Table> sqlFactory = this.getSqlFactoryRegistry(dialect)
				.getSqlFactory(obj, getSqlType());
		final List<SqlOperation> operations = sqlFactory.createSql(obj);
		this.getSqlExecutor().execute(operations);
	}

	/**
	 * @return the sqlType
	 */
	public SqlType getSqlType() {
		return sqlType;
	}

	/**
	 * @param sqlType
	 *            the sqlType to set
	 */
	public void setSqlType(final SqlType sqlType) {
		this.sqlType = sqlType;
	}

	/**
	 * @return the filter
	 */
	public Predicate<AbstractSchemaObject<?>> getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            the filter to set
	 */
	public void setFilter(final Predicate<AbstractSchemaObject<?>> filter) {
		this.filter = filter;
	}

}
