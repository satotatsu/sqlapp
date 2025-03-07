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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DefaultProperty;
import com.sqlapp.data.schemas.properties.LocationProperty;
import com.sqlapp.data.schemas.properties.ReadonlyProperty;
import com.sqlapp.data.schemas.properties.complex.OwnerProperty;
import com.sqlapp.data.schemas.properties.object.TableSpaceFilesProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * テーブルスペース、ファイルグループ(SQLServer)に相当するクラス
 * 
 * @author satoh
 * 
 */
public final class TableSpace extends AbstractNamedObject<TableSpace> implements
		HasParent<TableSpaceCollection>,ReadonlyProperty<TableSpace>
	,OwnerProperty<TableSpace>
	,DefaultProperty<TableSpace>
	,LocationProperty<TableSpace>
	,TableSpaceFilesProperty<TableSpace>{
	/** serialVersionUID */
	private static final long serialVersionUID = 3891888286502076232L;
	/**
	 * テーブルスペースのロケーション(Postgresの場合ディレクトリ)
	 */
	private String location = (String)SchemaProperties.LOCATION.getDefaultValue();
	/** テーブルスペースのオーナー */
	@SuppressWarnings("unused")
	private final User owner = null;
	/** テーブルスペースファイルコレクション */
	private TableSpaceFileCollection tableSpaceFiles = new TableSpaceFileCollection(
			this);
	/** デフォルトスペース */
	private boolean _default = (Boolean)SchemaProperties.DEFAULT.getDefaultValue();
	/** 読み込み専用 */
	private Boolean readonly = (Boolean)SchemaProperties.READONLY.getDefaultValue();

	@Override
	public boolean isDefault() {
		return _default;
	}

	@Override
	public TableSpace setDefault(final boolean _default) {
		this._default = _default;
		return this;
	}

	@Override
	public Boolean getReadonly() {
		return readonly;
	}

	@Override
	public TableSpace setReadonly(final Boolean readonly) {
		this.readonly = readonly;
		return this;
	}

	@Override
	protected TableSpace setParent(final DbCommonObject<?> parent){
		super.setParent(parent);
		return instance();
	}
	
	/**
	 * コンストラクタ
	 * 
	 */
	public TableSpace() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public TableSpace(final String name) {
		super(name);
	}

	@Override
	protected Supplier<TableSpace> newInstance(){
		return ()->new TableSpace();
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
	public TableSpace setLocation(final String location) {
		this.location = location;
		return instance();
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.LOCATION.getLabel(), this.getLocation());
		stax.writeAttribute(SchemaProperties.OWNER_NAME.getLabel(), this.getOwnerName());
		if (this.isDefault()) {
			stax.writeAttribute(SchemaProperties.DEFAULT.getLabel(), this.isDefault());
		}
		stax.writeAttribute(SchemaProperties.READONLY.getLabel(), this.getReadonly());
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(tableSpaceFiles)) {
			tableSpaceFiles.writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof TableSpace)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final TableSpace val = cast(obj);
		if (!equals(SchemaProperties.OWNER_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOCATION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.READONLY, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.TABLE_SPACE_FILES, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(final ToStringBuilder builder) {
		builder.add(SchemaProperties.OWNER_NAME, this.getOwnerName());
		builder.add(SchemaProperties.LOCATION, this.getLocation());
		builder.add(SchemaProperties.DEFAULT, this.isDefault());
		builder.add(SchemaProperties.READONLY, this.getOwnerName());
		builder.add(SchemaObjectProperties.TABLE_SPACE_FILES, this.getTableSpaceFiles());
	}

	/**
	 * @return the TableSpaceFileCollection
	 */
	@Override
	public TableSpaceFileCollection getTableSpaceFiles() {
		return tableSpaceFiles;
	}

	/**
	 * @param tableSpaceFiles
	 *            the tableSpaceFiles to set
	 */
	protected TableSpace setTableSpaceFiles(final TableSpaceFileCollection tableSpaceFiles) {
		this.tableSpaceFiles = tableSpaceFiles;
		if (this.tableSpaceFiles != null) {
			this.tableSpaceFiles.setParent(this);
		}
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public TableSpaceCollection getParent() {
		return (TableSpaceCollection) super.getParent();
	}

}
