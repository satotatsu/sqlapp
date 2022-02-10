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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DbLinkNameProperty;
import com.sqlapp.data.schemas.properties.SynonymProperties;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * シノニム
 * 
 * @author satoh
 * 
 */
public final class PublicSynonym extends AbstractNamedObject<PublicSynonym>
		implements HasParent<PublicSynonymCollection>
		,SynonymProperties<PublicSynonym>,DbLinkNameProperty<PublicSynonym> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4906000020243009507L;
	/** オブジェクトのスキーマ */
	private String objectSchemaName = null;
	/** オブジェクト名 */
	private String objectName = null;
	/** DBリンク名 */
	private String dbLinkName = null;

	/**
	 * コンストラクタ
	 */
	protected PublicSynonym() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public PublicSynonym(String name) {
		super(name);
	}

	@Override
	protected Supplier<PublicSynonym> newInstance(){
		return ()->new PublicSynonym();
	}
	
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.OBJECT_SCHEMA_NAME, this.getObjectSchemaName());
		builder.add(SchemaProperties.OBJECT_NAME, this.getObjectName());
		builder.add(SchemaProperties.DB_LINK_NAME, this.getDbLinkName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof PublicSynonym)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		PublicSynonym val = (PublicSynonym) obj;
		if (!equals(SchemaProperties.OBJECT_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.OBJECT_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DB_LINK_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.SynonymProperties#getObjectSchemaName()
	 */
	@Override
	public String getObjectSchemaName() {
		return objectSchemaName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.SynonymProperties#setObjectSchemaName(java.lang.String
	 * )
	 */
	@Override
	public PublicSynonym setObjectSchemaName(String objectSchemaName) {
		this.objectSchemaName = objectSchemaName;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.SynonymProperties#getObjectName()
	 */
	@Override
	public String getObjectName() {
		return objectName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.SynonymProperties#setObjectName(java.lang.String)
	 */
	@Override
	public PublicSynonym setObjectName(String objectName) {
		this.objectName = objectName;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.SynonymProperties#setDbLinkName(java.lang.String)
	 */
	@Override
	public PublicSynonym setDbLinkName(String dbLinkName) {
		this.dbLinkName = dbLinkName;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.SynonymProperties#getDbLinkName()
	 */
	@Override
	public String getDbLinkName() {
		return this.dbLinkName;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.OBJECT_SCHEMA_NAME.getLabel(), this.getObjectSchemaName());
		stax.writeAttribute(SchemaProperties.OBJECT_NAME.getLabel(), this.getObjectName());
		stax.writeAttribute(SchemaProperties.DB_LINK_NAME.getLabel(), this.getDbLinkName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public PublicSynonymCollection getParent() {
		return (PublicSynonymCollection) super.getParent();
	}

	public Table getTable(){
		Catalog catalog=this.getAncestor(Catalog.class);
		if (catalog==null){
			return null;
		}
		Schema schema=catalog.getSchemas().get(this.getObjectSchemaName());
		if (schema==null){
			return null;
		}
		return schema.getTables().get(this.getObjectName());
	}
	
}
