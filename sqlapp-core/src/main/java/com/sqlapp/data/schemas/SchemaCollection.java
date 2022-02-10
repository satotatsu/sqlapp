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

import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.function.AddDbObjectPredicate;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;

/**
 * スキーマのコレクションクラス
 * 
 * @author satoh
 * 
 */
public class SchemaCollection extends AbstractNamedObjectCollection<Schema>
		implements HasParent<Catalog>
	, RowIteratorHandlerProperty
	, NewElement<Schema, SchemaCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4540018510759477211L;
	
	/**
	 * コンストラクタ
	 */
	protected SchemaCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param catalog
	 */
	protected SchemaCollection(Catalog catalog) {
		super(catalog);
	}

	@Override
	protected Supplier<SchemaCollection> newInstance(){
		return ()->new SchemaCollection();
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof SchemaCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		SchemaCollection val = (SchemaCollection) obj;
		if (!equals(SchemaProperties.PRODUCT_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_MAJOR_VERSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_MINOR_VERSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRODUCT_REVISION, val, equalsHandler)) {
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
	public SchemaCollection clone() {
		return (SchemaCollection)super.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Catalog getParent() {
		return (Catalog) super.getParent();
	}

	public Catalog toCatalog(){
		if (this.getParent()!=null){
			return this.getParent();
		}
		Catalog catalog=new Catalog();
		if (this.size()>0){
			Schema schema=this.get(0);
			catalog.setProductName(schema.getProductName());
			catalog.setProductMajorVersion(schema.getProductMajorVersion());
			catalog.setProductMinorVersion(schema.getProductMinorVersion());
			catalog.setProductRevision(schema.getProductRevision());
			catalog.setCharacterSemantics(schema.getCharacterSemantics());
			catalog.setCollation(schema.getCollation());
			catalog.setCharacterSet(schema.getCharacterSet());
			catalog.getSchemas().addAll(this);
		}
		return catalog;
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	@Override
	public void setAddDbObjectPredicate(AddDbObjectPredicate addDbObjectFilter) {
		super.setAddDbObjectPredicate(addDbObjectFilter);
		for (Schema schema : this) {
			schema.setAddDbObjectFilter(addDbObjectFilter);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandlerProperty#setRowIteratorHandler
	 * (com.sqlapp.data.schemas.RowIteratorHandler)
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		for (Schema schema : this) {
			schema.setRowIteratorHandler(rowIteratorHandler);
		}
	}
	
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
	}

	/**
	 * @return the productVersionInfo
	 */
	protected ProductVersionInfo getParentProductVersionInfo() {
		Catalog catalog = this.getParent();
		if (catalog == null) {
			return null;
		}
		return catalog.getProductVersionInfo();
	}
	
	
	@Override
	protected void validate(){
		super.validate();
		for(Schema schema:this){
			for(Table table:schema.getTables()){
				List<ForeignKeyConstraint> fks=table.getConstraints().getForeignKeyConstraints();
				for(ForeignKeyConstraint fk:fks){
					if (!CommonUtils.eq(fk.getRelatedTableSchemaName(), table.getSchemaName())){
						Schema refSchema=this.get(fk.getRelatedTableSchemaName());
						if (refSchema!=null){
							Table refTable=refSchema.getTables().get(fk.getRelatedTableName());
							if (refTable!=null){
								refTable.addChildRelation(fk);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public Schema newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Schema> getElementSupplier() {
		return ()->new Schema();
	}
}
