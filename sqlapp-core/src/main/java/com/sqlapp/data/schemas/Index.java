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
import static com.sqlapp.util.CommonUtils.coalesce;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CompressionProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.IncludeColumnsProperty;
import com.sqlapp.data.schemas.properties.IndexTypeProperty;
import com.sqlapp.data.schemas.properties.PartitioningProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.UniqueProperty;
import com.sqlapp.data.schemas.properties.WhereProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * インデックス
 * 
 * @author satoh
 * 
 */
public final class Index extends AbstractSchemaObject<Index> implements
		HasParent<IndexCollection>, TableNameProperty<Index>
	, UniqueProperty<Index>
	, EnableProperty<Index>
	,ReferenceColumnsProperty<Index>
	,IncludeColumnsProperty<Index>
	,CompressionProperty<Index>
	,IndexTypeProperty<Index>
	,PartitioningProperty<Index>
	,WhereProperty<Index>
	,TableSpaceProperty<Index> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 259457460807208400L;
	/** ユニーク */
	private boolean unique = (Boolean)SchemaProperties.UNIQUE.getDefaultValue();
	/** インデックスタイプ */
	private IndexType indexType = null;
	/** 圧縮 */
	private boolean compression = (Boolean)SchemaProperties.COMPRESSION.getDefaultValue();
	/** インデックスカラム名の一覧 */
	private ReferenceColumnCollection columns = new ReferenceColumnCollection(
			this);
	/** 非クラスター化インデックスのリーフ レベルに、追加する非キー列 */
	private ReferenceColumnCollection includes = new ReferenceColumnCollection(
			this);
	/** パーティション情報 */
	private Partitioning partitioning = null;
	/** 部分インデックス条件(where以降) */
	private String where = null;
	/** テーブル名 */
	private String tableName = null;
	/** テーブルスペース */
	private TableSpace tableSpace = null;
	/** インデックスが有効かを表す */
	private boolean enable = (Boolean)SchemaProperties.ENABLE.getDefaultValue();

	@Override
	public boolean isEnable() {
		return enable;
	}

	@Override
	public Index setEnable(boolean enable) {
		this.enable = enable;
		return instance();
	}

	protected Index() {
	}

	@Override
	protected Supplier<Index> newInstance(){
		return ()->new Index();
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param indexName
	 */
	public Index(final String indexName) {
		super(indexName);
	}

	@Override
	public boolean isUnique() {
		return unique;
	}

	@Override
	public Index setUnique(final boolean unique) {
		this.unique = unique;
		return this;
	}

	@Override
	public IndexType getIndexType() {
		return indexType;
	}

	@Override
	public Index setIndexType(IndexType indexType) {
		this.indexType = indexType;
		return this;
	}

	/**
	 * インデックスの属するテーブルの取得
	 * 
	 */
	public final Table getTable() {
		if (getParent() == null) {
			return null;
		}
		return ((IndexCollection) getParent()).getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Index)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Index val = (Index) obj;
		if (!equals(SchemaProperties.UNIQUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.REFERENCE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.INCLUDED_COLUMNS, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.WHERE, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ENABLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMPRESSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.PARTITIONING, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
			builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		}
		builder.add(SchemaProperties.NAME, this.getName());
		builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, getColumns().toStringSimple());
		return builder.toString();
	}
	
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.UNIQUE, this.isUnique());
		builder.add(SchemaProperties.INDEX_TYPE, this.getIndexType());
		if (!CommonUtils.isEmpty(this.getColumns())) {
			builder.add(SchemaObjectProperties.REFERENCE_COLUMNS, this.getColumns().toStringSimple());
		}
		if (!CommonUtils.isEmpty(this.getIncludes())) {
			builder.add(SchemaObjectProperties.INCLUDED_COLUMNS, this.getIncludes().toStringSimple());
		}
		builder.add(SchemaProperties.WHERE, this.getWhere());
		builder.add(SchemaProperties.ENABLE, this.isEnable());
		builder.add(SchemaProperties.COMPRESSION, this.isCompression());
		builder.add(SchemaProperties.TABLE_SPACE_NAME, this.getTableSpaceName());
		builder.add(SchemaObjectProperties.PARTITIONING, this.getPartitioning());
	}

	@Override
	public ReferenceColumnCollection getColumns() {
		return columns;
	}

	protected Index setColumns(ReferenceColumnCollection columns) {
		if (columns != null) {
			columns.setParent(this);
		}
		this.columns = columns;
		return instance();
	}

	@Override
	public ReferenceColumnCollection getIncludes() {
		return includes;
	}

	protected Index setIncludes(ReferenceColumnCollection includes) {
		if (includes != null) {
			includes.setParent(this);
		}
		this.includes = includes;
		return instance();
	}

	
	/**
	 * @return カタログ名を取得します
	 */
	@Override
	public String getCatalogName() {
		if (getParent() != null) {
			IndexCollection indexes = cast(getParent());
			if (indexes.getParent() != null) {
				return coalesce(indexes.getParent().getCatalogName(),
						super.getCatalogName());
			}
		}
		return super.getCatalogName();
	}

	/**
	 * @return スキーマ名を取得します
	 */
	@Override
	public String getSchemaName() {
		if (getParent() != null) {
			IndexCollection indexes = cast(getParent());
			if (indexes.getParent() != null) {
				return coalesce(indexes.getParent().getSchemaName(), schemaName);
			}
		}
		return schemaName;
	}

	@Override
	public String getWhere() {
		return where;
	}

	@Override
	public Index setWhere(String where) {
		this.where = where;
		return instance();
	}

	/**
	 * @return the compression
	 */
	@Override
	public boolean isCompression() {
		return compression;
	}

	/**
	 * @param compression
	 *            the compression to set
	 */
	@Override
	public Index setCompression(boolean compression) {
		this.compression = compression;
		return this;
	}

	/**
	 * @return the partitioning
	 */
	@Override
	public Partitioning getPartitioning() {
		return partitioning;
	}

	/**
	 * @param partitioning
	 *            the partitioning to set
	 */
	@Override
	public Index setPartitioning(Partitioning partitioning) {
		if (this.partitioning != null) {
			this.partitioning.setIndex(null);
		}
		if (partitioning != null) {
			partitioning.setIndex(this);
		}
		this.partitioning = partitioning;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#getParent()
	 */
	@Override
	public IndexCollection getParent() {
		return (IndexCollection) super.getParent();
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.UNIQUE.getLabel(), this.isUnique());
		stax.writeAttribute(SchemaProperties.INDEX_TYPE.getLabel(), this.getIndexType());
		if (this.isCompression()) {
			stax.writeAttribute(SchemaProperties.COMPRESSION.getLabel(), this.isCompression());
		}
		stax.writeAttribute(SchemaProperties.TABLE_SPACE_NAME.getLabel(), this.getTableSpaceName());
		if (!this.isEnable()){
			stax.writeAttribute(SchemaProperties.ENABLE.getLabel(), this.isEnable());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(columns)) {
			columns.writeXml(stax);
		}
		if (!isEmpty(includes)) {
			includes.writeXml(SchemaObjectProperties.INCLUDED_COLUMNS.getLabel(), stax);
		}
		if (partitioning != null) {
			partitioning.writeXml(stax);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.TableNameProperty#getTableName()
	 */
	@Override
	public String getTableName() {
		if (this.getParent() != null) {
			IndexCollection indexes = cast(getParent());
			if (indexes.getParent() != null) {
				return indexes.getParent().getName();
			}
		}
		return tableName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.TableNameProperty#setTableName(java.lang.String)
	 */
	@Override
	public Index setTableName(String tableName) {
		this.tableName = tableName;
		return instance();
	}

	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof Index)){
			return false;
		}
		Index con=(Index)obj;
		if (!CommonUtils.eq(this.getName(), con.getName())){
			if (this.getParent()!=null&&con.getParent()!=null){
				if (this.getParent().contains(con.getName())||con.getParent().contains(this.getName())){
					return false;
				}
			}
		}
		if (!eq(this.getTableName(), con.getTableName())) {
			return false;
		}
		if (!eq(this.getColumns(), con.getColumns())) {
			return false;
		}
		return true;
	}
	
	@Override
	protected void validate(){
		super.validate();
		setTableSpace(this.getTableSpaceFromParent(tableSpace));
	}
}
