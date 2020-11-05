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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.MetaTypeProperty;
import com.sqlapp.data.schemas.properties.object.TypeColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ユーザー定義型(STRUCT)
 * 
 * @author satoh
 * 
 */
public final class Type extends AbstractSchemaObject<Type> implements
		HasParent<TypeCollection> ,CollationProperty<Type>
	,MetaTypeProperty<Type>
	,TypeColumnsProperty<Type>
	, Body<TypeBody> {
	/** serialVersionUID */
	private static final long serialVersionUID = -6503734410877376997L;
	/** メタタイプ */
	private MetaType metaType = MetaType.Default;
	/** TypeColumnのコレクション */
	private TypeColumnCollection columns = new TypeColumnCollection(this);
	/** collation */
	private String collation = null;
	/**
	 * コンストラクタ
	 */
	protected Type() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Type(String name) {
		super(name);
	}

	@Override
	protected Supplier<Type> newInstance(){
		return ()->new Type();
	}

	@Override
	protected void toString(ToStringBuilder builder) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Type)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Type val = (Type) obj;
		if (!equals(SchemaProperties.META_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COLLATION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.TYPE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.META_TYPE.getLabel(), this.getMetaType());
		builder.add(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		builder.add(SchemaObjectProperties.TYPE_COLUMNS, this.getColumns());
	}

	/**
	 * @return the columns
	 */
	public TypeColumnCollection getColumns() {
		return columns;
	}

	/**
	 * @param columns
	 *            the columns to set
	 */
	protected Type setColumns(TypeColumnCollection columns) {
		if (this.columns!=null){
			this.columns.setParent(null);
		}
		this.columns = columns;
		if (this.columns!=null){
			this.columns.setParent(this);
		}
		return this;
	}

	/**
	 * @return the metaType
	 */
	public MetaType getMetaType() {
		return metaType;
	}

	/**
	 * @param metaType
	 *            the metaType to set
	 */
	public Type setMetaType(MetaType metaType) {
		this.metaType = metaType;
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		if (!CommonUtils.eqIgnoreCase(getParentCollationName(),
				this.getCollation())) {
			stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		}
		if (this.metaType != MetaType.Default) {
			stax.writeAttribute(SchemaProperties.META_TYPE.getLabel(), this.getMetaType());
		}
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(columns)) {
			columns.writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#setCaseSensitive(boolean)
	 */
	@Override
	public Type setCaseSensitive(boolean caseSensitive) {
		columns.setCaseSensitive(caseSensitive);
		return super.setCaseSensitive(caseSensitive);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public TypeCollection getParent() {
		return (TypeCollection) super.getParent();
	}

	/**
	 * メタタイプ
	 * 
	 * @author satoh
	 * 
	 */
	public static enum MetaType {
		/**
		 * 行
		 */
		Row("r.*")
		/**
		 * カーソル
		 */
		, Cursor("c.*")
		/**
		 * デフォルト
		 */
		, Default("[^rc].*");
		MetaType(String patternText) {
			pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
		}

		final Pattern pattern;

		public static MetaType parse(String text) {
			if (text==null){
				return null;
			}
			for (MetaType metaType : values()) {
				Matcher matcher = metaType.pattern.matcher(text);
				if (matcher.matches()) {
					return metaType;
				}
			}
			return null;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.CollationName#getCollationName()
	 */
	@Override
	public String getCollation() {
		if (this.collation == null) {
			return getParentCollationName();
		}
		return this.collation;
	}

	private String getParentCollationName() {
		Schema schema = this.getAncestor(Schema.class);
		if (schema != null) {
			return schema.getCollation();
		}
		return null;
	}

	/**
	 * @param collationName
	 *            the collationName to set
	 */
	@Override
	public Type setCollation(String collationName) {
		this.collation = collationName;
		return this;
	}
	
	@Override
	public TypeBody getBody(){
		if (this.getParent()!=null&&this.getParent().getParent()!=null){
			return this.getParent().getParent().getTypeBodies().get(this.getName());
		}
		return null;
	}

}
