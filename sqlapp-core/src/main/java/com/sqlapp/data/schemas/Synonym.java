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
import com.sqlapp.data.schemas.properties.ObjectNameProperty;
import com.sqlapp.data.schemas.properties.ObjectSchemaNameProperty;
import com.sqlapp.data.schemas.properties.SynonymProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * シノニム
 * 
 * @author satoh
 * 
 */
public final class Synonym extends AbstractSchemaObject<Synonym> implements
		HasParent<SynonymCollection>, SynonymProperties<Synonym>
	, ObjectSchemaNameProperty<Synonym>
	, ObjectNameProperty<Synonym>
	, DbLinkNameProperty<Synonym>
{
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
	protected Synonym() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Synonym(String name) {
		super(name);
	}

	@Override
	protected Supplier<Synonym> newInstance(){
		return ()->new Synonym();
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
		if (!(obj instanceof Synonym)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Synonym val = (Synonym) obj;
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
	 * @see com.sqlapp.data.schemas.SynonymProperties#getObjectSchema()
	 */
	@Override
	public String getObjectSchemaName() {
		return objectSchemaName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.SynonymProperties#setObjectSchema(java.lang.String
	 * )
	 */
	@Override
	public Synonym setObjectSchemaName(String objectSchemaName) {
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
	public Synonym setObjectName(String objectName) {
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
	public Synonym setDbLinkName(String dbLinkName) {
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
	public SynonymCollection getParent() {
		return (SynonymCollection) super.getParent();
	}

	public Synonym nextSynonym(){
		if (this.getParent()==null){
			return null;
		}
		if (CommonUtils.eq(this.getSchemaName(), this.getObjectSchemaName())){
			return this.getParent().get(this.getObjectName());
		}
		SchemaCollection schemas=this.getAncestor(SchemaCollection.class);
		if (schemas==null){
			return null;
		}
		Schema schema=schemas.get(this.getObjectSchemaName());
		if (schema==null){
			return null;
		}
		return schema.getSynonyms().get(this.getObjectSchemaName());
	}

	public PublicSynonym nextPublicSynonym(){
		Catalog catalog=this.getAncestor(Catalog.class);
		if (catalog==null){
			return null;
		}
		return catalog.getPublicSynonyms().get(this.getObjectName());
	}

	public Synonym rootSynonym(){
		Synonym next=this;
		while(true){
			Synonym current=next.nextSynonym();
			if (current==null){
				return next;
			}
			next=current;
		}
	}

	public Table getTable(){
		Schema schema=this.getAncestor(Schema.class);
		if (schema==null){
			return null;
		}
		if (CommonUtils.eq(this.getSchemaName(), this.getObjectSchemaName())){
			return schema.getTable(this.getObjectName());
		}
		return schema.getTables().get(this.getObjectName());
	}
	
}
