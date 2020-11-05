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

/**
 * Privilegeのコレクション
 * 
 */
final class DummyTableCollection extends
		AbstractDbObjectCollection<DummyTable>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected DummyTableCollection() {
	}
	
	@Override
	protected Supplier<DummyTableCollection> newInstance(){
		return ()->new DummyTableCollection();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DummyTableCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	protected TableCollection toTableCollection(){
		TableCollection c=new TableCollection();
		for(DummyTable table:this){
			c.add(table.toTable());
		}
		return c;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObjectCollection#find(com.sqlapp.data
	 * .schemas.AbstractDbObject)
	 */
	@Override
	public DummyTable find(DummyTable obj) {
		if (obj == null) {
			return null;
		}
		for (DummyTable val : this) {
			if (val.like(obj)) {
				return val;
			}
		}
		return null;
	}

	@Override
	protected DummyTableCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new DummyTableCollectionXmlReaderHandler();
	}

	@Override
	protected Supplier<DummyTable> getElementSupplier() {
		return ()->new DummyTable();
	}
}
