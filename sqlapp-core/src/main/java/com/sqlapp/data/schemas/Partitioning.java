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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.PartitionSizeProperty;
import com.sqlapp.data.schemas.properties.PartitioningTypeProperty;
import com.sqlapp.data.schemas.properties.SubPartitionSizeProperty;
import com.sqlapp.data.schemas.properties.SubPartitioningTypeProperty;
import com.sqlapp.data.schemas.properties.complex.PartitionSchemeProperty;
import com.sqlapp.data.schemas.properties.object.PartitioningColumnsProperty;
import com.sqlapp.data.schemas.properties.object.PartitionsProperty;
import com.sqlapp.data.schemas.properties.object.SubPartitioningColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * パーティション
 * 
 * @author satoh
 * 
 */
public final class Partitioning extends AbstractDbObject<Partitioning>
		implements HasParent<AbstractSchemaObject<?>>
	,PartitionSchemeProperty<Partitioning>
	,PartitioningTypeProperty<Partitioning>
	,SubPartitioningTypeProperty<Partitioning>
	,PartitionSizeProperty<Partitioning>
	,SubPartitionSizeProperty<Partitioning>
	,PartitioningColumnsProperty<Partitioning>
	,SubPartitioningColumnsProperty<Partitioning>
	,PartitionsProperty<Partitioning>
	{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8518120516204639184L;

	/**
	 * デフォルトコンストラクタ
	 */
	public Partitioning() {
	}

	/**
	 * コンストラクタ
	 */
	public Partitioning(final PartitioningType partitioningType) {
		this.partitioningType = partitioningType;
	}

	/**
	 * コンストラクタ
	 */
	protected Partitioning(final AbstractSchemaObject<?> parent) {
		this.setParent(parent);
	}

	@Override
	protected Supplier<Partitioning> newInstance(){
		return ()->new Partitioning();
	}
	
	/**
	 * パーティションの所属するテーブル
	 */
	private Table table = null;
	/**
	 * パーティションの所属するインデックス
	 */
	private Index index = null;
	/**
	 * パーティションスキーム(SQL Server)
	 */
	private PartitionScheme partitionScheme = null;
	/** パーティションタイプ */
	private PartitioningType partitioningType = null;
	/** サブパーティションタイプ */
	private PartitioningType subPartitioningType = null;
	/** パーティション対象のカラムのコレクション */
	private ReferenceColumnCollection partitioningColumns = new ReferenceColumnCollection(this);
	/** サブパーティション対象のカラムのコレクション */
	private ReferenceColumnCollection subPartitioningColumns = new ReferenceColumnCollection(this);
	/** パーティション */
	private PartitionCollection partitions = new PartitionCollection(this);
	/** パーティションテーブル*/
	private final List<Table> partitionTables=CommonUtils.list();
	/** パーティションのサイズ */
	private Integer partitionSize = null;
	/** サブパーティションのサイズ */
	private Integer subPartitionSize = null;
	
	
	/**
	 * equals
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Partitioning)) {
			return false;
		}
		final Partitioning val = (Partitioning) obj;
		if (!equals(SchemaProperties.PARTITIONING_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SUB_PARTITIONING_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.PARTITIONING_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.SUB_PARTITIONING_COLUMNS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PARTITION_SIZE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SUB_PARTITION_SIZE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.PARTITIONS, val,	equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PARTITION_SCHEME_NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toString(final ToStringBuilder builder) {
		builder.add(SchemaProperties.PARTITIONING_TYPE, this.getPartitioningType());
		builder.add(SchemaProperties.PARTITION_SCHEME_NAME, this.getPartitionSchemeName());
		builder.add(SchemaProperties.SUB_PARTITIONING_TYPE, this.getSubPartitioningType());
		builder.add(SchemaObjectProperties.PARTITIONING_COLUMNS, this.getPartitioningColumns());
		builder.add(SchemaObjectProperties.SUB_PARTITIONING_COLUMNS, this.getSubPartitioningColumns());
		builder.add(SchemaObjectProperties.PARTITIONS, this.getPartitions());
		super.toString(builder);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		final ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		builder.add(SchemaProperties.PARTITIONING_TYPE, this.getPartitioningType());
		builder.add(SchemaProperties.PARTITION_SCHEME_NAME, this.getPartitionSchemeName());
		builder.add(SchemaProperties.SUB_PARTITIONING_TYPE, this.getSubPartitioningType());
		if (!CommonUtils.isEmpty(this.getPartitioningColumns())){
			builder.add(SchemaObjectProperties.PARTITIONING_COLUMNS, this.getPartitioningColumns().toStringSimple());
		}
		if (!CommonUtils.isEmpty(this.getSubPartitioningColumns())){
			builder.add(SchemaObjectProperties.SUB_PARTITIONING_COLUMNS, this.getSubPartitioningColumns().toStringSimple());
		}
		return builder.toString();
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.PARTITION_SCHEME_NAME.getLabel(), this.getPartitionSchemeName());
		stax.writeAttribute(SchemaProperties.PARTITIONING_TYPE.getLabel(), this.getPartitioningType());
		stax.writeAttribute(SchemaProperties.SUB_PARTITIONING_TYPE.getLabel(), this.getSubPartitioningType());
		stax.writeAttribute(SchemaProperties.PARTITION_SIZE.getLabel(), this.getPartitionSize());
		stax.writeAttribute(SchemaProperties.SUB_PARTITION_SIZE.getLabel(), this.getSubPartitionSize());
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(partitioningColumns)) {
			partitioningColumns.writeXml(SchemaObjectProperties.PARTITIONING_COLUMNS.getLabel(), stax);
		}
		if (!isEmpty(subPartitioningColumns)) {
			subPartitioningColumns.writeXml(SchemaObjectProperties.SUB_PARTITIONING_COLUMNS.getLabel(), stax);
		}
		if (!isEmpty(partitions)) {
			partitions.writeXml(stax);
		}
	}

	@Override
	public PartitioningType getPartitioningType() {
		return this.partitioningType;
	}

	@Override
	public Partitioning setPartitioningType(final PartitioningType partitioningType) {
		this.partitioningType = partitioningType;
		return instance();
	}

	/**
	 * @return the subPartitioningType
	 */
	@Override
	public PartitioningType getSubPartitioningType() {
		return subPartitioningType;
	}

	/**
	 * @param subPartitioningType
	 *            the subPartitioningType to set
	 */
	@Override
	public Partitioning setSubPartitioningType(final PartitioningType subPartitioningType) {
		this.subPartitioningType = subPartitioningType;
		return instance();
	}

	/**
	 * @return the partitioningColumns
	 */
	@Override
	public ReferenceColumnCollection getPartitioningColumns() {
		return partitioningColumns;
	}

	/**
	 * @param partitioningColumns
	 *            the partitioningColumns to set
	 */
	public Partitioning setPartitioningColumns(
			final ReferenceColumnCollection partitioningColumns) {
		if (partitioningColumns != null) {
			partitioningColumns.setParent(this);
		}
		this.partitioningColumns = partitioningColumns;
		return instance();
	}

	/**
	 * @return the subPartitioningColumns
	 */
	@Override
	public ReferenceColumnCollection getSubPartitioningColumns() {
		return subPartitioningColumns;
	}

	/**
	 * @param subpartitioningColumns
	 *            the subPartitioningColumns to set
	 */
	public Partitioning setSubpartitioningColumns(
			final ReferenceColumnCollection subpartitioningColumns) {
		if (subpartitioningColumns != null) {
			subpartitioningColumns.setParent(this);
		}
		this.subPartitioningColumns = subpartitioningColumns;
		return instance();
	}

	/**
	 * @return the table
	 */
	public Table getTable() {
		return table;
	}

	/**
	 * @param table
	 *            the table to set
	 */
	protected void setTable(final Table table) {
		partitioningColumns.setTable(table);
		subPartitioningColumns.setTable(table);
		this.table = table;
	}

	/**
	 * @return the index
	 */
	public Index getIndex() {
		return index;
	}

	/**
	 * @param index
	 *            the index to set
	 */
	protected void setIndex(final Index index) {
		partitioningColumns.setTable(index.getTable());
		subPartitioningColumns.setTable(index.getTable());
		this.index = index;
	}
	
	/**
	 * @return the parent
	 */
	@Override
	public AbstractSchemaObject<?> getParent() {
		return (AbstractSchemaObject<?>)super.getParent();
	}

	/**
	 * @param parent
	 *            the parent to set
	 */
	protected Partitioning setParent(final AbstractSchemaObject<?> parent) {
		if (parent instanceof Table) {
			this.table = (Table) parent;
		} else if (parent instanceof Index) {
			this.index = (Index) parent;
		}
		super.setParent(parent);
		return instance();
	}
	
	protected Partitioning setSubPartitioningColumns(final ReferenceColumnCollection subPartitioningColumns){
		this.subPartitioningColumns = subPartitioningColumns;
		if (subPartitioningColumns!=null){
			subPartitioningColumns.setParent(this);
		}
		return instance();
	}
	
	protected Partitioning setPartitions(final PartitionCollection partitions){
		this.partitions = partitions;
		if (partitions!=null){
			partitions.setParent(this);
		}
		return instance();
	}

	/**
	 * @return the partitions
	 */
	@Override
	public PartitionCollection getPartitions() {
		return partitions;
	}

	public List<Table> getPartitionTables(){
		return Collections.unmodifiableList(partitionTables);
	}

	protected void addPartitionTable(final Table table){
		if (partitionTables.contains(table)) {
			return;
		}
		partitionTables.add(table);
		partitionTables.sort((o1,o2)->{
			return o1.compareTo(o2);
		});
	}

	protected void addAllPartitionTable(final Collection<Table> tables){
		tables.forEach(table->{
			if (partitionTables.contains(table)) {
				return;
			}
			partitionTables.add(table);
		});
		partitionTables.sort((o1,o2)->{
			return o1.compareTo(o2);
		});
	}

	
	protected void removePartitionTable(final Table table){
		if (!partitionTables.contains(table)) {
			return;
		}
		partitionTables.remove(table);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#setCaseSensitive(boolean)
	 */
	protected void setCaseSensitive(final boolean caseSensitive) {
		partitioningColumns.setCaseSensitive(caseSensitive);
		subPartitioningColumns.setCaseSensitive(caseSensitive);
	}

	@Override
	public int compareTo(final Partitioning o) {
		return 0;
	}

	/**
	 * @return the partitionSize
	 */
	@Override
	public Integer getPartitionSize() {
		if (CommonUtils.isEmpty(this.getPartitions())) {
			return partitionSize;
		}
		return this.getPartitions().size();
	}

	/**
	 * @param partitionSize
	 *            the partitionSize to set
	 */
	@Override
	public Partitioning setPartitionSize(final Integer partitionSize) {
		this.partitionSize = partitionSize;
		return instance();
	}

	/**
	 * @return the subPartitionSize
	 */
	@Override
	public Integer getSubPartitionSize() {
		return subPartitionSize;
	}

	/**
	 * @param subpartitionSize
	 *            the subPartitionSize to set
	 */
	@Override
	public Partitioning setSubPartitionSize(final Integer subpartitionSize) {
		this.subPartitionSize = subpartitionSize;
		return instance();
	}

	@Override
	protected PartitioningHandler getDbObjectXmlReaderHandler() {
		return new PartitioningHandler();
	}

	/**
	 * スキーマの内容をバリデートします
	 */
	@Override
	protected void validate() {
		if (this.partitionScheme!=null){
			final Catalog catalog=this.getAncestor(Catalog.class);
			if (catalog!=null){
				if (!CommonUtils.isEmpty(catalog.getPartitionSchemes())){
					final PartitionScheme partitionScheme=catalog.getPartitionSchemes().get(this.partitionScheme.getName());
					if (this.partitionScheme!=partitionScheme){
						this.partitionScheme=partitionScheme;
					}
					generatePartitionsByPartitionScheme();
				}
			}
		}
	}
	
	private void generatePartitionsByPartitionScheme(){
		if (this.getPartitionScheme()==null){
			return;
		}
		this.getPartitions().clear();
		final PartitionFunction partitionFunction=this.getPartitionScheme().getPartitionFunction();
		for(int i=0;i<partitionFunction.getValues().size()+1;i++){
			final TableSpace tableSpace=this.getPartitionScheme().getTableSpaces().get(i);
			final Partition partition=new Partition();
			if (partitionFunction.isBoundaryValueOnRight()){
				partition.setHighValueInclusive(true);
			}
			partition.setVirtual(true);
			if (i<partitionFunction.getValues().size()){
				final String value=partitionFunction.getValues().get(i);
				partition.setName(value);
				partition.setHighValue(value);
			}
			partition.setTableSpace(tableSpace);
			this.getPartitions().add(partition);
		}
	}
}
