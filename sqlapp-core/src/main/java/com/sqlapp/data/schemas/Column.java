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
import static com.sqlapp.util.CommonUtils.cloneMap;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.DefaultConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.CheckProperty;
import com.sqlapp.data.schemas.properties.HiddenProperty;
import com.sqlapp.data.schemas.properties.OnUpdateProperty;
import com.sqlapp.data.schemas.properties.PrimaryKeyGetter;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.EmptyTextSkipHandler;
import com.sqlapp.util.xml.MapHandler;

/**
 * DataColumn
 * 
 */
public final class Column extends AbstractColumn<Column> implements
		HasParent<ColumnCollection>, TableNameProperty<Column>, HiddenProperty<Column>,OnUpdateProperty<Column>,PrimaryKeyGetter
		,CheckProperty<Column>
		{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7183150115593442021L;

	public Column() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param columnName
	 */
	public Column(String columnName) {
		super(columnName);
	}

	@Override
	protected Supplier<Column> newInstance(){
		return ()->new Column();
	}

	/**
	 * テーブル名
	 */
	private String tableName = null;
	/** on update */
	private String onUpdate = null;
	/** チェック制約 */
	private CheckConstraint checkConstraint = null;
	/** コンバーター */
	private Converter<?> converter = null;
	/** コンバーターの変数名 */
	public static final String CONVERTER = "converter";
	/** 拡張プロパティ */
	private Map<String, Object> extendedProperties = null;
	/** 拡張プロパティの変数名 */
	protected static final String EXTENDED_PROPERTIES = "extendedProperties";
	/** 非表示の変数 */
	private boolean hidden = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractColumn#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Column)) {
			return false;
		}
		Column val = (Column) obj;
		if (!equals(SchemaProperties.CHECK, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HIDDEN, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ON_UPDATE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public String getCheck() {
		if (this.getCheckConstraint() == null) {
			return null;
		}
		return this.getCheckConstraint().getExpression();
	}

	@Override
	protected void cloneProperties(Column clone) {
		super.cloneProperties(clone);
		clone.setConverter(this.getConverter());
		clone.setExtendedProperties(cloneMap(getExtendedProperties()));
		clone.setCheckConstraint(this.getCheckConstraint());
	}

	@SuppressWarnings("rawtypes")
	public Converter getConverter() {
		if (converter == null) {
			if (this.getDataType() != null) {
				converter = Converters.getDefault().getConverter(
						this.getDataType().getDefaultClass());
			}
			if (converter == null) {
				converter = new DefaultConverter();
			}
		}
		return converter;
	}

	public Column setConverter(Converter<?> converter) {
		this.converter = converter;
		return this;
	}
	public Map<String, Object> getExtendedProperties() {
		if (extendedProperties == null) {
			extendedProperties = new HashMap<String, Object>();
		}
		return extendedProperties;
	}

	public Column setExtendedProperties(Map<String, Object> extendedProperties) {
		this.extendedProperties = extendedProperties;
		return this;
	}

	@Override
	public ColumnCollection getParent() {
		return cast(super.getParent());
	}

	protected Column setColumns(ColumnCollection columns) {
		this.setParent(columns);
		return this;
	}

	@Override
	public Column setDataType(DataType type) {
		DataType oldType = this.getDataType();
		if (oldType!=type){
			super.setDataType(type);
			this.converter = null;
			Table table = this.getTable();
			if (table != null && oldType != type) {
				if (table.getRows().getRowIteratorHandler() instanceof DefaultRowIteratorHandler){
					for (Row row : table.getRows()) {
						Object obj = row.get(this);
						row.put(this, obj);
					}
				}
			}
		}
		return instance();
	}

	/**
	 * カラムの属するテーブルの取得
	 * 
	 */
	public Table getTable() {
		return this.getAncestor(Table.class);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		if (!isEmpty(this.getCheckConstraint())) {
			builder.add(SchemaProperties.CHECK, this.getCheck());
		}
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
			builder.add(SchemaProperties.CATALOG_NAME, this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
			builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		}
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		return builder.toString();
	}

	public CheckConstraint getCheckConstraint() {
		return checkConstraint;
	}

	public Column setCheckConstraint(CheckConstraint checkConstraint) {
		this.checkConstraint = checkConstraint;
		return this;
	}

	@Override
	public Column setCheck(String check) {
		if (check != null) {
			CheckConstraint c = new CheckConstraint();
			this.setCheckConstraint(c);
		}
		if (this.getCheckConstraint() != null) {
			this.getCheckConstraint().setExpression(check);
		}
		return this;
	}

	/**
	 * @return カタログ名を取得します
	 */
	@Override
	public String getCatalogName() {
		Table table = this.getTable();
		if (table != null) {
			return table.getCatalogName();
		}
		return super.getCatalogName();
	}

	/**
	 * @return スキーマ名を取得します
	 */
	@Override
	public String getSchemaName() {
		Table table = this.getTable();
		if (table != null) {
			return table.getSchemaName();
		}
		return super.getSchemaName();
	}

	/**
	 * テーブル名を取得します。
	 * 
	 */
	@Override
	public String getTableName() {
		Table table = this.getTable();
		if (table != null) {
			return table.getName();
		}
		return tableName;
	}

	/**
	 * テーブル名を設定します。
	 * 
	 * @param tableName
	 */
	@Override
	public Column setTableName(String tableName) {
		this.tableName = tableName;
		return instance();
	}

	@Override
	public String getOnUpdate() {
		return onUpdate;
	}

	@Override
	public Column setOnUpdate(String onUpdate) {
		this.onUpdate=onUpdate;
		return instance();
	}

	/**
	 * @return the hidden
	 */
	@Override
	public boolean isHidden() {
		return hidden;
	}

	/**
	 * @param hidden the hidden to set
	 */
	@Override
	public Column setHidden(boolean hidden) {
		this.hidden = hidden;
		return instance();
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.CHECK, this);
		stax.writeAttribute(SchemaProperties.ON_UPDATE, this);
		if (this.isHidden()){
			stax.writeAttribute(SchemaProperties.HIDDEN, this);
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(this.getExtendedProperties())) {
			stax.newLine();
			stax.indent();
			stax.writeElement(EXTENDED_PROPERTIES, this.getExtendedProperties());
		}
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Column> getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler<Column>(this.newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				// Other
				String keyName = Column.EXTENDED_PROPERTIES;
				register(keyName, new AbstractSetValue<Column, Map<String, Object>>() {
					@Override
					public void setValue(Column target, String name,
							Map<String, Object> setValue) throws XMLStreamException {
						target.setExtendedProperties(setValue);
					}
				});
				registerTransparent(keyName, new MapHandler(),
						new EmptyTextSkipHandler());
			}

			@Override
			protected ColumnCollection toParent(Object parentObject) {
				ColumnCollection parent = null;
				if (parentObject instanceof ColumnCollection) {
					parent = (ColumnCollection) parentObject;
				}
				return parent;
			}
		};
	}

	@Override
	public boolean isPrimaryKey(){
		if(this.getTable()==null){
			return false;
		}
		UniqueConstraint uc=this.getTable().getConstraints().getPrimaryKeyConstraint();
		if (uc==null){
			return false;
		}
		for(ReferenceColumn column:uc.getColumns()){
			if (column.getColumn()!=null){
				if (CommonUtils.eq(this.getName(), column.getColumn().getName())){
					return true;
				}
			} else{
				if (CommonUtils.eq(this.getName(), column.getName())){
					return true;
				}
			}
		}
		return false;
	}

	public boolean isForeignKey(){
		if(this.getTable()==null){
			return false;
		}
		for(ForeignKeyConstraint fk:this.getTable().getConstraints().getForeignKeyConstraints()){
			for(Column column:fk.getColumns()){
				if (CommonUtils.eq(this.getName(), column.getName())){
					return true;
				}
			}
		}
		return false;
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (equals(obj, IncludeFilterEqualsHandler.EQUALS_NAME_HANDLER)) {
			return true;
		} else {
			if (!equals(obj,
					ExcludeFilterEqualsHandler.EQUALS_WITHOUT_NAME_HANDLER)) {
				return false;
			}
			Column column = (Column) obj;
			if (this.getOrdinal() != column.getOrdinal()) {
				return false;
			}
			if (this.getParent() == null || column.getParent() == null) {
				return true;
			}
			// 他に同じ名前のがある場合はそちらを優先
			Column eqName = column.getParent().get(this.getName());
			if (eqName != null) {
				return false;
			}
			eqName = this.getParent().get(column.getName());
			if (eqName != null) {
				return false;
			}
			return true;
		}
	}
	
	@Override
	public Column setName(String name) {
		final String origianlName=this.getName();
		if(CommonUtils.eq(name, origianlName)){
			return instance();
		}
		Table table=this.getTable();
		if (table!=null){
			//change child relation
			List<ReferenceColumnCollection> childColumnsList=table.getChildRelations().stream().filter(fk->fk.getRelatedColumns()!=null).filter(obj->{
				for(ReferenceColumn refColumn:obj.getRelatedColumns()){
					if (CommonUtils.eq(refColumn.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(fk->fk.getRelatedColumns()).collect(Collectors.toList());
			changeReferenceColumnName(origianlName, name, childColumnsList);
			//change parent relation
			List<Column[]> parentColumnsList=table.getConstraints().getForeignKeyConstraints().stream().filter(fk->fk.getColumns()!=null).filter(obj->{
				for(Column column:obj.getColumns()){
					if (CommonUtils.eq(column.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(fk->fk.getColumns()).collect(Collectors.toList());
			changeColumnName(origianlName, name, parentColumnsList);
			//change index columns
			List<ReferenceColumnCollection> indexColumnsList=table.getIndexes().stream().filter(obj->{
				for(ReferenceColumn refColumn:obj.getColumns()){
					if (CommonUtils.eq(refColumn.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(obj->obj.getColumns()).collect(Collectors.toList());
			changeReferenceColumnName(origianlName, name, indexColumnsList);
			//change index include columns
			List<ReferenceColumnCollection> indexIncludesColumnsList=table.getIndexes().stream().filter(obj->{
				for(ReferenceColumn refColumn:obj.getIncludes()){
					if (CommonUtils.eq(refColumn.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(obj->obj.getColumns()).collect(Collectors.toList());
			changeReferenceColumnName(origianlName, name, indexIncludesColumnsList);
			//change unique constraint columns
			List<ReferenceColumnCollection> ucColumnsList=table.getConstraints().getUniqueConstraints().stream().filter(obj->{
				for(ReferenceColumn refColumn:obj.getColumns()){
					if (CommonUtils.eq(refColumn.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(obj->obj.getColumns()).collect(Collectors.toList());
			changeReferenceColumnName(origianlName, name, ucColumnsList);
			//change unique constraint columns
			List<ReferenceColumnCollection> ecColumnsList=table.getConstraints().getExcludeConstraints().stream().filter(obj->{
				for(ReferenceColumn refColumn:obj.getColumns()){
					if (CommonUtils.eq(refColumn.getName(), origianlName)){
						return true;
					}
				}
				return false;
			}).map(obj->obj.getColumns()).collect(Collectors.toList());
			changeReferenceColumnName(origianlName, name, ecColumnsList);
			//change partitioning columns
			changeReferenceColumnName(origianlName, name, ecColumnsList);
			if (table.getPartitioning()!=null){
				changeReferenceColumnName(origianlName, name, table.getPartitioning().getPartitioningColumns());
				changeReferenceColumnName(origianlName, name, table.getPartitioning().getSubPartitioningColumns());
			}
			//change index partitioning columns
			table.getIndexes().stream().filter(obj->obj.getPartitioning()!=null).map(obj->obj.getPartitioning()).forEach(p->{
				changeReferenceColumnName(origianlName, name, p.getPartitioningColumns());
				changeReferenceColumnName(origianlName, name, p.getSubPartitioningColumns());
			});
		}
		super.setName(name);
		return instance();
	}

	private void changeReferenceColumnName(String originalName, String name, List<ReferenceColumnCollection> columnsList){
		if (CommonUtils.isEmpty(columnsList)){
			return;
		}
		columnsList.forEach(cc->{
			cc.forEach(c->{
				if (CommonUtils.eq(c.getName(), originalName)){
					c.setName(name);
				}
			});
		});
		columnsList.forEach(cc->{
			cc.renew();
		});
	}

	private void changeReferenceColumnName(String originalName, String name, ReferenceColumnCollection columns){
		if (CommonUtils.isEmpty(columns)){
			return;
		}
		columns.forEach(c->{
			if (CommonUtils.eq(c.getName(), originalName)){
				c.setName(name);
			}
		});
		columns.renew();
	}

	private void changeColumnName(String originalName, String name, List<Column[]> columnsList){
		if (columnsList==null){
			return;
		}
		columnsList.forEach(cc->{
			for(Column column:cc){
				if (CommonUtils.eq(column.getName(), originalName)){
					column.setName(name);
				}
			}
		});
	}
}