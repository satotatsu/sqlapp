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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.AccessParametersProperty;
import com.sqlapp.data.schemas.properties.AccessTypeProperty;
import com.sqlapp.data.schemas.properties.DefaultDirectoryNameProperty;
import com.sqlapp.data.schemas.properties.DirectoryNameProperty;
import com.sqlapp.data.schemas.properties.LocationProperty;
import com.sqlapp.data.schemas.properties.PropertyProperty;
import com.sqlapp.data.schemas.properties.RejectLimitProperty;
import com.sqlapp.data.schemas.properties.TypeNameProperty;
import com.sqlapp.data.schemas.properties.TypeSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * 外部テーブル(for Oracle)
 * 
 * @author satoh
 * 
 */
public final class ExternalTable extends AbstractSchemaObject<ExternalTable>
		implements HasParent<ExternalTableCollection>
		,TypeSchemaNameProperty<ExternalTable> 
		,TypeNameProperty<ExternalTable> 
		,DefaultDirectoryNameProperty<ExternalTable> 
		,DirectoryNameProperty<ExternalTable> 
		,LocationProperty<ExternalTable> 
		,RejectLimitProperty<ExternalTable> 
		,AccessTypeProperty<ExternalTable> 
		,AccessParametersProperty<ExternalTable> 
		,PropertyProperty<ExternalTable> 
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	/** 外部表アクセス・ドライバの実装タイプ */
	private Type type = null;
	/**
	 * 外部表アクセス・ドライバの実装タイプの所有者
	 */
	// private String defaultDirectoryOwner=null;
	/** 外部表アクセス・ドライバの実装タイプの名前 */
	private Directory defaultDirectory = null;
	/**
	 * 外部表の位置を含むディレクトリの所有者
	 */
	// private String directoryOwner=null;
	/** 外部表の位置を含むディレクトリ */
	private Directory directory = null;
	/** 外部表の位置を示す句 */
	private String location = null;
	/** 外部表の拒否制限、またはUNLIMITED */
	private String rejectLimit = null;
	/**
	 * 外部表のアクセス・パラメータのタイプ BLOB CLOB
	 */
	private String accessType = null;
	/**
	 * 外部表のアクセス・パラメータ
	 */
	private String accessParameters = null;
	/**
	 * 外部表のプロパティ REFERENCED － 参照される列 ALL － すべての列
	 */
	private String property = null;

	/**
	 * コンストラクタ
	 */
	public ExternalTable() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public ExternalTable(String name) {
		super(name);
	}

	@Override
	protected Supplier<ExternalTable> newInstance(){
		return ()->new ExternalTable();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof ExternalTable)) {
			return false;
		}
		ExternalTable val = (ExternalTable) obj;
		if (!equals(SchemaProperties.TYPE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TYPE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT_DIRECTORY_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DIRECTORY_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOCATION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.REJECT_LIMIT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ACCESS_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ACCESS_PARAMETERS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PROPERTY, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.TYPE_NAME, this.getTypeName());
		builder.add(SchemaProperties.DEFAULT_DIRECTORY_NAME, this.getDefaultDirectoryName());
		builder.add(SchemaProperties.DIRECTORY_NAME, this.getDirectoryName());
		builder.add(SchemaProperties.LOCATION, this.getLocation());
		builder.add(SchemaProperties.REJECT_LIMIT, this.getRejectLimit());
		builder.add(SchemaProperties.ACCESS_TYPE, this.getAccessType());
		builder.add(SchemaProperties.ACCESS_PARAMETERS, this.getAccessParameters());
		builder.add(SchemaProperties.PROPERTY, this.getProperty());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.TYPE_SCHEMA_NAME.getLabel(), this.getTypeSchemaName());
		stax.writeAttribute(SchemaProperties.TYPE_NAME.getLabel(), this.getTypeName());
		stax.writeAttribute(SchemaProperties.DEFAULT_DIRECTORY_NAME.getLabel(), this.getDefaultDirectoryName());
		stax.writeAttribute(SchemaProperties.DIRECTORY_NAME.getLabel(), this.getDirectoryName());
		stax.writeAttribute(SchemaProperties.LOCATION.getLabel(), this.getLocation());
		stax.writeAttribute(SchemaProperties.REJECT_LIMIT.getLabel(), this.getRejectLimit());
		stax.writeAttribute(SchemaProperties.ACCESS_TYPE.getLabel(), this.getAccessType());
		stax.writeAttribute(SchemaProperties.PROPERTY.getLabel(), this.getProperty());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(getAccessParameters())) {
			stax.newLine();
			stax.writeCData(SchemaProperties.ACCESS_PARAMETERS.getLabel(), getAccessParameters());
		}
	}

	@Override
	public ExternalTableCollection getParent() {
		return (ExternalTableCollection) super.getParent();
	}

	/**
	 * @return the type
	 */
	public Type getType() {
		if (this.type != null && this.type.getParent() == null) {
			this.type = getTypeFromParent(type);
		}
		return type;
	}

	/**
	 * @return the typeSchemaName
	 */
	@Override
	public String getTypeSchemaName() {
		if (type == null) {
			return null;
		}
		return type.getSchemaName();
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public ExternalTable setType(Type type) {
		if (type != null && type.getParent() == null) {
			this.type = getTypeFromParent(type);
		} else {
			this.type = type;
		}
		return this;
	}
	
	@Override
	public String getTypeName(){
		if (this.getType()==null){
			return null;
		}
		return this.getType().getName();
	}
	
	/**
	 * @param typeSchemaName
	 *            the typeSchemaName to set
	 */
	@Override
	public ExternalTable setTypeSchemaName(String typeSchemaName) {
		if (this.getType()==null){
			this.type = new Type();
		}
		this.getType().setSchemaName(typeSchemaName);
		return instance();
	}
	
	/**
	 * @param typeName
	 *            the typeName to set
	 */
	@Override
	public ExternalTable setTypeName(String typeName) {
		if (isEmpty(typeName)) {
			this.type = null;
		} else {
			if (this.getType()!=null){
				if (!CommonUtils.eq(this.getType().getName(),typeName)){
					this.setType(new Type(typeName));
				}
			} else{
				this.setType(new Type(typeName));
			}
		}
		return this;
	}

	protected Type getTypeFromParent(Type type) {
		if (this.getParent() == null) {
			return type;
		}
		if (this.getParent().getSchema() == null) {
			return type;
		}
		if (eq(this.getTypeSchemaName(), type.getSchemaName())) {
			Type getType = this.getParent().getSchema().getTypes()
					.get(type.getName());
			if (getType != null) {
				return getType;
			}

		} else {
			if (this.getParent().getSchema().getParent() == null) {
				return type;
			}
			Schema schema = this.getParent().getSchema().getParent()
					.get(type.getSchemaName());
			if (schema == null) {
				return type;
			}
			Type getType = schema.getTypes().get(type.getName());
			if (getType != null) {
				return getType;
			}
		}
		return type;
	}

	/**
	 * @return the defaultDirectory
	 */
	public Directory getDefaultDirectory() {
		if (this.defaultDirectory != null
				&& this.defaultDirectory.getParent() == null) {
			this.defaultDirectory = getDirectoryFromParent(defaultDirectory);
		}
		return defaultDirectory;
	}

	protected Directory getDirectoryFromParent(Directory directory) {
		Catalog catalog = this.getAncestor(Catalog.class);
		if (catalog == null) {
			return directory;
		}
		Directory getDirectory = catalog.getDirectories().get(
				directory.getName());
		if (getDirectory != null) {
			return getDirectory;
		}
		return directory;
	}

	/**
	 * @return the defaultDirectoryName
	 */
	@Override
	public String getDefaultDirectoryName() {
		if (defaultDirectory == null) {
			return null;
		}
		return getDefaultDirectory().getName();
	}

	/**
	 * @param defaultDirectory
	 *            the defaultDirectory to set
	 */
	public ExternalTable setDefaultDirectory(Directory defaultDirectory) {
		if (defaultDirectory != null && defaultDirectory.getParent() == null) {
			this.defaultDirectory = getDirectoryFromParent(defaultDirectory);
		} else {
			this.defaultDirectory = defaultDirectory;
		}
		return this;
	}

	/**
	 * @param defaultDirectoryName
	 *            the defaultDirectoryName to set
	 */
	@Override
	public ExternalTable setDefaultDirectoryName(String defaultDirectoryName) {
		if (isEmpty(defaultDirectoryName)) {
			this.defaultDirectory = null;
		} else {
			this.setDefaultDirectory(new Directory(defaultDirectoryName));
		}
		return this;
	}

	/**
	 * @return the directory
	 */
	public Directory getDirectory() {
		if (this.directory == null) {
			return this.getDefaultDirectory();
		}
		if (this.directory.getParent() == null) {
			this.directory = getDirectoryFromParent(directory);
		}
		return directory;
	}

	/**
	 * @return the directoryName
	 */
	@Override
	public String getDirectoryName() {
		if (getDirectory() == null) {
			return null;
		}
		return getDirectory().getName();
	}

	/**
	 * @param directory
	 *            the directory to set
	 */
	public ExternalTable setDirectory(Directory directory) {
		if (directory != null && directory.getParent() == null) {
			this.directory = getDirectoryFromParent(directory);
		} else {
			this.directory = directory;
		}
		return this;
	}

	/**
	 * @param directory
	 *            the directory to set
	 */
	@Override
	public ExternalTable setDirectoryName(String directory) {
		if (isEmpty(directory)) {
			this.directory = null;
		} else {
			this.setDirectory(new Directory(directory));
		}
		return this;
	}

	/**
	 * @return the location
	 */
	@Override
	public String getLocation() {
		return location;
	}

	/**
	 * @param location
	 *            the location to set
	 */
	@Override
	public ExternalTable setLocation(String location) {
		this.location = location;
		return this;
	}

	/**
	 * @return the rejectLimit
	 */
	@Override
	public String getRejectLimit() {
		return rejectLimit;
	}

	/**
	 * @param rejectLimit
	 *            the rejectLimit to set
	 */
	@Override
	public ExternalTable setRejectLimit(String rejectLimit) {
		this.rejectLimit = rejectLimit;
		return this;
	}

	/**
	 * @return the accessType
	 */
	@Override
	public String getAccessType() {
		return accessType;
	}

	/**
	 * @param accessType
	 *            the accessType to set
	 */
	@Override
	public ExternalTable setAccessType(String accessType) {
		this.accessType = accessType;
		return this;
	}

	/**
	 * @return the accessParameters
	 */
	@Override
	public String getAccessParameters() {
		return accessParameters;
	}

	/**
	 * @param accessParameters
	 *            the accessParameters to set
	 */
	@Override
	public ExternalTable setAccessParameters(String accessParameters) {
		this.accessParameters = accessParameters;
		return instance();
	}

	/**
	 * @return the property
	 */
	@Override
	public String getProperty() {
		return property;
	}

	/**
	 * @param property
	 *            the property to set
	 */
	@Override
	public ExternalTable setProperty(String property) {
		this.property = property;
		return instance();
	}

}
