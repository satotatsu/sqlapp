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

package com.sqlapp.data.schemas;

import java.util.function.Supplier;

public final class TableSpaceFileCollection extends
		AbstractNamedObjectCollection<TableSpaceFile> implements
		HasParent<TableSpace>
, NewElement<TableSpaceFile, TableSpaceFileCollection>{

	/** serialVersionUID */
	private static final long serialVersionUID = -1098985877736989046L;

	/**
	 * コンストラクタ
	 */
	protected TableSpaceFileCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param tableSpace
	 */
	public TableSpaceFileCollection(TableSpace tableSpace) {
		super(tableSpace);
	}

	@Override
	protected Supplier<TableSpaceFileCollection> newInstance(){
		return ()->new TableSpaceFileCollection();
	}
	
	@Override
	public TableSpace getParent() {
		return (TableSpace)super.getParent();
	}

	/**
	 * 追加後のメソッド
	 */
	@Override
	protected void afterAdd(TableSpaceFile arg) {
		arg.setTableSpaceName(null);
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TableSpaceFileCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractSchemaObjectList#clone()
	 */
	@Override
	public TableSpaceFileCollection clone() {
		return (TableSpaceFileCollection)super.clone();
	}

	@Override
	public TableSpaceFile newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<TableSpaceFile> getElementSupplier() {
		return ()->new TableSpaceFile();
	}
}
