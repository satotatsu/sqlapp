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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.trim;
import static com.sqlapp.util.CommonUtils.xor;

import java.io.Serializable;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.SchemaNameGetter;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * スキーマのオブジェクト抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSchemaObject<T extends AbstractSchemaObject<T>>
		extends AbstractNamedObject<T> implements Serializable, Cloneable,
		SchemaNameProperty<T> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 210101856540540272L;
	/** スキーマ名 */
	protected String schemaName = null;

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractSchemaObject() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractSchemaObject(final String name) {
		super(name);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 * @param specificName
	 */
	protected AbstractSchemaObject(final String name, final String specificName) {
		super(name, specificName);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T setName(final String name) {
		super.setName(name);
		return (T) this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return super.hashCode() ^ xor(this.getSchemaName(), this.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof AbstractSchemaObject)) {
			return false;
		}
		final T val = cast(obj);
		if (this.getParent() == null && val.getParent() == null) {
			if (!equals(SchemaProperties.SCHEMA_NAME, val, equalsHandler)) {
				return false;
			}
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		if (!eq(this.getName(), this.getSpecificName())) {
			builder.add(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
		}
		toString(builder);
		return builder.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		final ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		if (!CommonUtils.eq(this.getName(), this.getSpecificName())&&this.getSpecificName()!=null){
			builder.add(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
		}
		return builder.toString();
	}

	protected T setParent(final AbstractSchemaObjectCollection<?> parent) {
		super.setParent(parent);
		return instance();
	}

	/**
	 * @return スキーマ名を取得します
	 */
	@Override
	public String getSchemaName() {
		if (getParent() != null) {
			final SchemaNameGetter schemaNameGetter=this.getAncestor(p->p instanceof SchemaNameGetter);
			String name =null;
			if (schemaNameGetter!=null){
				name=schemaNameGetter.getSchemaName();
				return name;
			}
			final Schema schema=this.getAncestor(p->p instanceof Schema);
			if (schema!=null){
				name=schema.getName();
			}
			return name;
		}
		return schemaName;
	}

	/**
	 * @param schemaName
	 *            スキーマ名を設定します
	 */
	@Override
	public T setSchemaName(final String schemaName) {
		this.schemaName = trim(schemaName);
		return instance();
	}
	
	public Schema getSchema(){
		return this.getAncestor(Schema.class);
	}

	/**
	 * 名称のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeName(final StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), getName());
		if (!eq(getName(), getSpecificName())) {
			stax.writeAttribute(SchemaProperties.SPECIFIC_NAME.getLabel(), this.getSpecificName());
		}
	}

	/**
	 * 共通属性要素のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeCommonNameAttribute(final StaxWriter stax)
			throws XMLStreamException {
		if (this.getParent() == null) {
			stax.writeAttribute(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
	}

	protected void writeSimpleXml(final StaxWriter stax, final boolean writeCommon)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeElement(getSimpleName(), ()->{
			writeName(stax);
			// writeXmlOptionalAttributes(stax);
			stax.indent(()->{
				// writeXmlOptionalValues(stax);
				if (writeCommon) {
					writeCommonAttribute(stax);
				}
			});
		});
	}

	protected void writeSimpleXmlWithSchema(final StaxWriter stax)
			throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeElement(getSimpleName(), ()->{
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
			writeName(stax);
			stax.newLine();
			stax.indent();
		});
	}
}
