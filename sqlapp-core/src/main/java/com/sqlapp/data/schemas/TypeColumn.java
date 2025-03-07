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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CheckProperty;
import com.sqlapp.data.schemas.properties.TypeNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ユーザー定義型(STRUCT)のカラム
 * 
 */
public final class TypeColumn extends AbstractColumn<TypeColumn> implements
		HasParent<TypeColumnCollection>
	, TypeNameProperty<TypeColumn>
	, CheckProperty<TypeColumn>
{
	/** serialVersionUID */
	private static final long serialVersionUID = 8775419796577781694L;

	public TypeColumn() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public TypeColumn(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<TypeColumn> newInstance(){
		return ()->new TypeColumn();
	}
	
	/**
	 * タイプ名
	 */
	private String typeName = null;
	/**
	 * 制約式
	 */
	private String check = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractColumn#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TypeColumn)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		TypeColumn val = (TypeColumn) obj;
		if (!equals(SchemaProperties.CHECK, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		if (!isEmpty(this.getCheck())) {
			builder.add(SchemaProperties.CHECK, this.getCheck());
		}
	}

	public String getCheck() {
		return check;
	}

	public TypeColumn setCheck(String check) {
		this.check = check;
		return this;
	}

	@Override
	protected String getSimpleName(){
		return "column";
	}
	
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.CHECK.getLabel(), this.getCheck());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public TypeColumnCollection getParent() {
		return (TypeColumnCollection) super.getParent();
	}

	public Type getType() {
		TypeColumnCollection columns = getParent();
		if (columns == null) {
			return null;
		}
		return columns.getParent();
	}

	/**
	 * @return カタログ名を取得します
	 */
	@Override
	public String getCatalogName() {
		Type type = getType();
		if (type != null) {
			return null;
		}
		return super.getCatalogName();
	}

	/**
	 * @return スキーマ名を取得します
	 */
	@Override
	public String getSchemaName() {
		Type type = getType();
		if (type != null) {
			return null;
		}
		return super.getSchemaName();
	}

	@Override
	public String getTypeName() {
		Type type = getType();
		if (type != null) {
			return type.getName();
		}
		return typeName;
	}
	
	@Override
	public TypeColumn setTypeName(String typeName) {
		this.typeName = typeName;
		return this;
	}
}