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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.AutoExtensibleProperty;
import com.sqlapp.data.schemas.properties.FilePathProperty;
import com.sqlapp.data.schemas.properties.TableSpaceNameProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * テーブルスペースもしくはファイルグループの使用するファイルに対応したオブジェクト
 * 
 * @author satoh
 * 
 */
public final class TableSpaceFile extends AbstractNamedObject<TableSpaceFile>
		implements HasParent<TableSpaceFileCollection>
	, TableSpaceNameProperty<TableSpaceFile>, FilePathProperty<TableSpaceFile>
	, AutoExtensibleProperty<TableSpaceFile>
	{
	/** serialVersionUID */
	private static final long serialVersionUID = 5364113040918889046L;
	/** テーブルスペース名 */
	private String tableSpaceName = null;
	/** ファイルパス */
	private String filePath = null;
	/** 自動拡張 */
	private boolean autoExtensible = (Boolean)SchemaProperties.AUTO_EXTENSIBLE.getDefaultValue();

	public TableSpaceFile() {
	}

	public TableSpaceFile(String name, String filePath) {
		super(name);
	}

	@Override
	protected Supplier<TableSpaceFile> newInstance(){
		return ()->new TableSpaceFile();
	}
	
	/**
	 * @return the filePath
	 */
	@Override
	public String getFilePath() {
		return this.filePath;
	}

	/**
	 * @param filePath
	 *            the filePath to set
	 */
	@Override
	public TableSpaceFile setFilePath(String filePath) {
		this.filePath = filePath;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TableSpaceFile)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		TableSpaceFile val = cast(obj);
		if (!equals(SchemaProperties.FILE_PATH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.AUTO_EXTENSIBLE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.FILE_PATH, this.getFilePath());
		builder.add(SchemaProperties.AUTO_EXTENSIBLE, this.isAutoExtensible());
	}

	/**
	 * @return the autoExtensible
	 */
	@Override
	public boolean isAutoExtensible() {
		return autoExtensible;
	}

	/**
	 * @param autoExtensible
	 *            the autoExtensible to set
	 */
	@Override
	public TableSpaceFile setAutoExtensible(boolean autoExtensible) {
		this.autoExtensible = autoExtensible;
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!eq(this.getName(), this.getFilePath())) {
			stax.writeAttribute(SchemaProperties.FILE_PATH.getLabel(), this.getFilePath());
		}
		stax.writeAttribute(SchemaProperties.AUTO_EXTENSIBLE.getLabel(), this.isAutoExtensible());
	}

	/**
	 * スペース名を取得します
	 * 
	 */
	@Override
	public String getTableSpaceName() {
		TableSpace parent = this.getAncestor(TableSpace.class);
		if (parent != null) {
			return parent.getName();
		}
		return tableSpaceName;
	}

	@Override
	public TableSpaceFile setTableSpaceName(String tableSpaceName) {
		this.tableSpaceName = tableSpaceName;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public TableSpaceFileCollection getParent() {
		return (TableSpaceFileCollection) super.getParent();
	}

}
