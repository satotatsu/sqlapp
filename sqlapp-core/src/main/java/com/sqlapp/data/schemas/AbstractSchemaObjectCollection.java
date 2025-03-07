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

import static com.sqlapp.util.CommonUtils.cast;

public abstract class AbstractSchemaObjectCollection<T extends AbstractSchemaObject<? super T>>
		extends AbstractNamedObjectCollection<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4540018510759477211L;

	/**
	 * コンストラクタ
	 */
	protected AbstractSchemaObjectCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param parent
	 */
	protected AbstractSchemaObjectCollection(DbCommonObject<?> parent) {
		this.setParent(parent);
		this.setSchema(this.getAncestor(Schema.class));
	}

	
	/**
	 * コンストラクタ
	 * 
	 * @param schema
	 */
	protected AbstractSchemaObjectCollection(Schema schema) {
		this.setParent(schema);
		this.setSchema(schema);
	}

	/**
	 * データセット
	 */
	private Schema schema = null;

	@Override
	public AbstractSchemaObjectCollection<T> clone() {
		return cast(super.clone());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AbstractSchemaObjectCollection<?>)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return true;
	}

	/**
	 * スキーマを取得します
	 * 
	 */
	public Schema getSchema() {
		return schema;
	}

	protected void setSchema(Schema schema) {
		this.schema = schema;
	}

	/**
	 * @return スキーマ名を取得します
	 */
	protected String getSchemaName() {
		if (getSchema() != null) {
			return getSchema().getName();
		}
		return null;
	}

	/**
	 * スキーマ情報の初期化
	 * 
	 * @param e
	 */
	@Override
	protected void initializeSchemaInfo(T e) {
		super.initializeSchemaInfo(e);
		if (equalsIgnoreCase(e.schemaName, getSchemaName())) {
			e.setSchemaName(null);
		} else {
			e.setSchemaName(getSchemaName());
		}
	}
}
