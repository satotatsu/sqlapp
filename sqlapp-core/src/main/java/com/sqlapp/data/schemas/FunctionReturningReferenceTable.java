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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table.TableDataStoreType;
import com.sqlapp.data.schemas.Table.TableType;
import com.sqlapp.data.schemas.properties.CharacterSemanticsProperty;
import com.sqlapp.data.schemas.properties.CharacterSetProperty;
import com.sqlapp.data.schemas.properties.CollationProperty;
import com.sqlapp.data.schemas.properties.CompressionProperty;
import com.sqlapp.data.schemas.properties.IndexTableSpaceNameProperty;
import com.sqlapp.data.schemas.properties.LobTableSpaceNameProperty;
import com.sqlapp.data.schemas.properties.PartitioningProperty;
import com.sqlapp.data.schemas.properties.ReadonlyProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableDataStoreTypeProperty;
import com.sqlapp.data.schemas.properties.TableSpaceNameProperty;
import com.sqlapp.data.schemas.properties.TableTypeProperty;
import com.sqlapp.data.schemas.properties.object.ColumnsProperty;
import com.sqlapp.data.schemas.properties.object.ConstraintsProperty;
import com.sqlapp.data.schemas.properties.object.IndexesProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxReader;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.xml.AbstractSetValue;
import com.sqlapp.util.xml.StaxElementHandler;

/**
 * Tableの参照を保持するオブジェクト
 * 
 * @author satoh
 * 
 */
public final class FunctionReturningReferenceTable extends
		AbstractNamedObject<FunctionReturningReferenceTable> implements
		HasParent<FunctionReturning>
	, SchemaNameProperty<FunctionReturningReferenceTable>
	, CollationProperty<FunctionReturningReferenceTable>
	, CharacterSetProperty<FunctionReturningReferenceTable>
	, CharacterSemanticsProperty<FunctionReturningReferenceTable>
	, Mergeable<FunctionReturningReferenceTable>
	, ColumnsProperty<FunctionReturningReferenceTable>
	, ConstraintsProperty<FunctionReturningReferenceTable>
	, IndexesProperty<FunctionReturningReferenceTable>
	, TableSpaceNameProperty<FunctionReturningReferenceTable>
	, IndexTableSpaceNameProperty<FunctionReturningReferenceTable>
	, LobTableSpaceNameProperty<FunctionReturningReferenceTable>
	, TableTypeProperty<FunctionReturningReferenceTable>
	, TableDataStoreTypeProperty<FunctionReturningReferenceTable>
	, PartitioningProperty<FunctionReturningReferenceTable>
	, ReadonlyProperty<FunctionReturningReferenceTable>
	, CompressionProperty<FunctionReturningReferenceTable> {

	/** serialVersionUID */
	private static final long serialVersionUID = -6483603161537304602L;
	/**
	 * テーブル
	 */
	private Table table = new Table();

	@Override
	protected Supplier<FunctionReturningReferenceTable> newInstance(){
		return ()->new FunctionReturningReferenceTable();
	}

	@Override
	public String getCatalogName() {
		return null;
	}

	@Override
	public String getSchemaName() {
		return null;
	}

	@Override
	public FunctionReturningReferenceTable setSchemaName(String value) {
		return this;
	}
	
	protected Table getTable(){
		return this.table;
	}

	/**
	 * コンストラクタ
	 */
	protected FunctionReturningReferenceTable() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param parent
	 */
	public FunctionReturningReferenceTable(FunctionReturning parent) {
		this.setParent(parent);
	}

	@Override
	protected String getSimpleName() {
		return DbObjects.TABLE.getCamelCase();
	}

	@Override
	public String getName() {
		return this.getTable().getName();
	}

	/**
	 * 名称を設定します
	 * 
	 */
	@Override
	public FunctionReturningReferenceTable setName(String name) {
		this.getTable().setName(name);
		return this;
	}
	
	@Override
	protected void writeXml(String name, StaxWriter stax)
			throws XMLStreamException {
		this.table.writeXml(name, stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof FunctionReturningReferenceTable)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		FunctionReturningReferenceTable val = cast(obj);
		if (!this.table.equals(val.getTable(), equalsHandler)){
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	public FunctionReturningReferenceTable setTable(Table table){
		if (table!=null){
			Table clone=table.clone();
			clone.getRows().clear();
			this.table=clone;
		}
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#like(java.lang.Object)
	 */
	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof FunctionReturningReferenceTable)) {
			return false;
		}
		FunctionReturningReferenceTable cst = (FunctionReturningReferenceTable) obj;
		if (!CommonUtils.eq(this.getName(), cst.getName())) {
			return false;
		}
		return true;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		this.getTable().toStringDetail(builder);
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<FunctionReturningReferenceTable> getDbObjectXmlReaderHandler() {
		return new AbstractNamedObjectXmlReaderHandler<FunctionReturningReferenceTable>(this.newInstance()) {
			@Override
			protected void initializeSetValue() {
				super.initializeSetValue();
				// Other
				StaxElementHandler handler = new PrimaryKeyConstraintXmlReaderHandler();
				register(handler.getLocalName(),
						new AbstractSetValue<FunctionReturningReferenceTable, UniqueConstraint>() {
							@Override
							public void setValue(FunctionReturningReferenceTable target, String name,
									UniqueConstraint setValue)
									throws XMLStreamException {
								target.getConstraints().add(setValue);
							}
						});
				registerChild(handler);
				this.setAlias(handler.getLocalName(), SchemaProperties.PRIMARY_KEY.getLabel());
			}

			@Override
			protected void finishDoHandle(StaxReader reader, Object parentObject,
					FunctionReturningReferenceTable table) {
				ConstraintCollection constraints = table.getConstraints();
				constraints.setParent(table);
			}
		};
	}
	
	@Override
	public ColumnCollection getColumns() {
		return this.getTable().getColumns();
	}

	@Override
	public ConstraintCollection getConstraints() {
		return this.getTable().getConstraints();
	}

	@Override
	public IndexCollection getIndexes() {
		return this.getTable().getIndexes();
	}

	@Override
	public boolean isCompression() {
		return this.getTable().isCompression();
	}

	@Override
	public FunctionReturningReferenceTable setCompression(boolean value) {
		this.getTable().setCompression(value);
		return instance();
	}

	@Override
	public Boolean getReadonly() {
		return this.getTable().getReadonly();
	}

	@Override
	public FunctionReturningReferenceTable setReadonly(Boolean readonly) {
		this.getTable().setReadonly(readonly);
		return instance();
	}

	@Override
	public Partitioning getPartitioning() {
		return this.getTable().getPartitioning();
	}

	@Override
	public FunctionReturningReferenceTable setPartitioning(Partitioning value) {
		this.getTable().setPartitioning(value);
		return instance();
	}

	@Override
	public TableDataStoreType getTableDataStoreType() {
		return this.getTable().getTableDataStoreType();
	}

	@Override
	public FunctionReturningReferenceTable setTableDataStoreType(TableDataStoreType value) {
		this.getTable().setTableDataStoreType(value);
		return instance();
	}

	@Override
	public FunctionReturningReferenceTable setTableDataStoreType(String value) {
		this.getTable().setTableDataStoreType(value);
		return instance();
	}

	@Override
	public TableType getTableType() {
		return this.getTable().getTableType();
	}

	@Override
	public FunctionReturningReferenceTable setTableType(TableType value) {
		this.getTable().setTableType(value);
		return instance();
	}

	@Override
	public FunctionReturningReferenceTable setTableType(String value) {
		this.getTable().setTableType(value);
		return instance();
	}

	@Override
	public void merge(FunctionReturningReferenceTable obj) {
		this.getTable().merge(obj.getTable());
	}

	@Override
	public CharacterSemantics getCharacterSemantics() {
		return this.getTable().getCharacterSemantics();
	}

	@Override
	public FunctionReturningReferenceTable setCharacterSemantics(CharacterSemantics value) {
		this.getTable().setCharacterSemantics(value);
		return instance();
	}

	@Override
	public FunctionReturningReferenceTable setCharacterSemantics(String value) {
		this.getTable().setCharacterSemantics(value);
		return instance();
	}

	@Override
	public String getCharacterSet(){
		return this.getTable().getCharacterSet();
	}
	
	@Override
	public FunctionReturningReferenceTable setCharacterSet(String value) {
		this.getTable().setCharacterSet(value);
		return instance();
	}

	@Override
	public String getCollation(){
		return this.getTable().getCollation();
	}

	@Override
	public FunctionReturningReferenceTable setCollation(String value) {
		this.getTable().setCollation(value);
		return instance();
	}

	@Override
	public FunctionReturning getParent() {
		return (FunctionReturning)super.getParent();
	}

	@Override
	public String getLobTableSpaceName() {
		return this.getTable().getLobTableSpaceName();
	}

	@Override
	public FunctionReturningReferenceTable setLobTableSpaceName(String value) {
		this.getTable().setLobTableSpaceName(value);
		return instance();
	}

	@Override
	public String getIndexTableSpaceName() {
		return this.getTable().getIndexTableSpaceName();
	}

	@Override
	public FunctionReturningReferenceTable setIndexTableSpaceName(String value) {
		this.getTable().setIndexTableSpaceName(value);
		return instance();
	}

	@Override
	public String getTableSpaceName() {
		return this.getTable().getTableSpaceName();
	}

	@Override
	public FunctionReturningReferenceTable setTableSpaceName(String value) {
		this.getTable().setTableSpaceName(value);
		return instance();
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	@Override
	public FunctionReturningReferenceTable setDialect(Dialect dialect) {
		this.table.setDialect(dialect);
		return super.setDialect(dialect);
	}
}
