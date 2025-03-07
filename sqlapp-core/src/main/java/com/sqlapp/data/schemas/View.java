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

/**
 * ビュークラス
 * 
 * @author tatsuo satoh
 * 
 */
public class View extends Table {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6046986932957158585L;

	/**
	 * デフォルトコンストラクタ
	 */
	public View() {
	}

	/**
	 * コンストラクタ
	 */
	public View(String tableName) {
		super(tableName);
	}

	@Override
	protected Supplier<Table> newInstance(){
		return ()->new View();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Table#equals(java.lang.Object,
	 * com.sqlapp.data.schemas.EqualsHandler)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof View)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return true;
	}

	@Override
	protected TableXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new TableXmlReaderHandler(this.newInstance()) {
			@Override
			public String getLocalName(){
				return "view";
			}
			@Override
			protected TableCollection toParent(Object parentObject) {
				TableCollection parent = null;
				if (parentObject instanceof TableCollection) {
					parent = (TableCollection) parentObject;
				} else if (parentObject instanceof Schema) {
					Schema schema = (Schema) parentObject;
					parent = schema.getViews();
				}
				return parent;
			}
		};
	}
	
	@Override
	public ViewCollection getParent(){
		return (ViewCollection)super.getParent();
	}
}
