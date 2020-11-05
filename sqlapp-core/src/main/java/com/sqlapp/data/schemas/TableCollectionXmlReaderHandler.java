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
package com.sqlapp.data.schemas;

import java.util.function.Supplier;

import com.sqlapp.util.StaxReader;

/**
 * TableCollectionのXML読み込み
 * 
 * @author satoh
 * 
 */
class TableCollectionXmlReaderHandler extends
AbstractNamedObjectCollectionXmlReaderHandler<TableCollection> {

	public TableCollectionXmlReaderHandler() {
		super(()->new TableCollection());
	}
	
	protected TableCollectionXmlReaderHandler(Supplier<TableCollection> supplier) {
		super(supplier);
	}

	@Override
	protected void initializeSetValue() {
		super.initializeSetValue();
		setChild(new Table().getDbObjectXmlReaderHandler());
	}
	
	@Override
	protected void finishDoHandle(StaxReader reader, Object parentObject,
			TableCollection tables) {
		setConstraints(tables);
	}

	/**
	 * 制約のカラムをTableのカラムに変更する
	 * 
	 * @param table
	 */
	private void setConstraints(TableCollection tables) {
		int size = tables.size();
		for (int i = 0; i < size; i++) {
			setConstraints(tables, tables.get(i));
		}
	}

	private void setConstraints(TableCollection tables, Table table) {
		ConstraintCollection constraints = table.getConstraints();
		int size = constraints.size();
		for (int i = 0; i < size; i++) {
			Constraint con = constraints.get(i);
			if (con instanceof ForeignKeyConstraint) {
				ForeignKeyConstraint fk = (ForeignKeyConstraint) con;
				ReferenceColumnCollection cols = fk.getRelatedColumns();
				setColumns(tables, cols);
			}
		}
	}

	private void setColumns(TableCollection tables, ReferenceColumnCollection columns) {
		int size = columns.size();
		for (int i = 0; i < size; i++) {
			ReferenceColumn column = columns.get(i);
			Table table = tables.get(column.getTable().getName());
			if (table != null) {
				Column fkColumn = table.getColumns().get(column.getName());
				column.setColumn(fkColumn);
			}
		}
	}

}
