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
import static com.sqlapp.util.CommonUtils.list;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;

/**
 * 制約のコレクション
 * 
 * @author satoh
 * 
 */
public final class ConstraintCollection extends
		AbstractSchemaObjectCollection<Constraint> implements Cloneable,
		HasParent<Table> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6835895264159969740L;

	/**
	 * コンストラクタ
	 */
	protected ConstraintCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected ConstraintCollection(final Table table) {
		super(table);
	}

	@Override
	protected Supplier<ConstraintCollection> newInstance(){
		return ()->new ConstraintCollection();
	}
	
	@Override
	public ConstraintCollection clone(){
		return (ConstraintCollection)super.clone();
	}

	
	/**
	 * Primary Key制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addPrimaryKeyConstraint(final String constraintName,
			final Column... columns) {
		return addUniqueConstraint(constraintName, true, columns);
	}

	/**
	 * Primary Key制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columnNames
	 *            制約のあるカラム
	 */
	public UniqueConstraint addPrimaryKeyConstraint(final String constraintName,
			final String... columnNames) {
		final List<Column> columns=CommonUtils.list();
		for(final String columnName:columnNames){
			final Column column=this.getParent().getColumns().get(columnName);
			if (column==null){
				throw new IllegalArgumentException("columnName="+columnName);
			}
			columns.add(column);
		}
		return addUniqueConstraint(constraintName, true, columns);
	}

	
	/**
	 * Primary Key制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addPrimaryKeyConstraint(final String constraintName,
			final Collection<Column> columns) {
		return addUniqueConstraint(constraintName, true, columns);
	}

	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final boolean primaryKey, final Collection<Column> columns) {
		return addUniqueConstraint(constraintName, primaryKey,
				columns.toArray(new Column[0]));
	}

	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final boolean primaryKey, final Column... columns) {
		final UniqueConstraint uc = new UniqueConstraint(constraintName, primaryKey,
				columns);
		if (primaryKey) {
			final UniqueConstraint pk = this.getPrimaryKeyConstraint();
			if (pk != null) {
				pk.setPrimaryKey(primaryKey);
			}
			for(final Column column:columns){
				column.setNotNull(true);
			}
		}
		add(uc);
		return uc;
	}

	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param primaryKey
	 *            プライマリーキー
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final boolean primaryKey, final ReferenceColumn... columns) {
		final UniqueConstraint uc = new UniqueConstraint(constraintName, primaryKey,
				columns);
		if (primaryKey) {
			final UniqueConstraint pk = this.getPrimaryKeyConstraint();
			if (pk != null) {
				pk.setPrimaryKey(primaryKey);
			}
			for(final ReferenceColumn column:columns){
				if (column.getColumn()!=null) {
					column.getColumn().setNotNull(true);
				}
			}
		}
		add(uc);
		return uc;
	}

	
	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final Column... columns) {
		return addUniqueConstraint(constraintName, false, columns);
	}


	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final ReferenceColumn... columns) {
		return addUniqueConstraint(constraintName, false, columns);
	}

	
	/**
	 * ユニーク制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 *            制約のあるカラム
	 */
	public UniqueConstraint addUniqueConstraint(final String constraintName,
			final Collection<Column> columns) {
		return addUniqueConstraint(constraintName, false, columns);
	}

	/**
	 * チェック制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param expression
	 *            チェック制約式
	 * @param columns
	 *            制約のあるカラム
	 */
	public CheckConstraint addCheckConstraint(final String constraintName,
			final String expression, final Column... columns) {
		final CheckConstraint c = new CheckConstraint(constraintName, expression,
				columns);
		add(c);
		return c;
	}

	/**
	 * チェック制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param expression
	 *            チェック制約式
	 * @param columns
	 *            制約のあるカラム
	 */
	public CheckConstraint addCheckConstraint(final String constraintName,
			final String expression, final Collection<Column> columns) {
		return addCheckConstraint(constraintName, expression,
				columns.toArray(new Column[0]));
	}

	/**
	 * 外部キー制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param columns
	 * @param parentColumns
	 */
	public ForeignKeyConstraint addForeignKeyConstraint(final String constraintName,
			final Column[] columns, final Column[] parentColumns) {
		final ForeignKeyConstraint fc = new ForeignKeyConstraint(constraintName,
				columns, parentColumns);
		add(fc);
		return fc;
	}

	/**
	 * 外部キー制約を追加します
	 * 
	 * @param constraintName
	 *            制約名
	 * @param column
	 * @param parentColumn
	 */
	public ForeignKeyConstraint addForeignKeyConstraint(final String constraintName,
			final Column column, final Column parentColumn) {
		final ForeignKeyConstraint fc = new ForeignKeyConstraint(constraintName,
				column, parentColumn);
		add(fc);
		return fc;
	}

	/**
	 * ユニーク制約を追加します
	 * 
	 * @param uc
	 *            チェック制約
	 */
	public void add(final UniqueConstraint uc) {
		super.add(uc);
		if (this.getTable() == null) {
			return;
		}
		uc.setTableName(null);
		resetColumns(uc);
		sort();
	}

	/**
	 * ユニーク制約を追加します
	 * 
	 * @param uc
	 *            チェック制約
	 */
	public void add(final ExcludeConstraint uc) {
		super.add(uc);
		if (this.getTable() == null) {
			return;
		}
		uc.setTableName(null);
		resetColumns(uc);
		sort();
	}

	/**
	 * Exclude制約のカラムを再設定します
	 * 
	 * @param constraint
	 */
	protected void resetColumns(final ExcludeConstraint constraint) {
		constraint.getColumns().setTable(this.getParent());
	}

	/**
	 * ユニーク制約のカラムを再設定します
	 * 
	 * @param uc
	 */
	protected void resetColumns(final UniqueConstraint uc) {
		uc.getColumns().setTable(this.getParent());
	}

	/**
	 * チェック制約を追加します
	 * 
	 * @param cc
	 *            チェック制約
	 */
	public void add(final CheckConstraint cc) {
		super.add(cc);
		if (this.getTable() == null) {
			return;
		}
		resetColumns(cc);
		sort();
	}

	/**
	 * 追加後のメソッド
	 */
	@Override
	protected void afterAdd(final Constraint c) {
		if (c instanceof UniqueConstraint) {
			resetColumns((UniqueConstraint) c);
		} else if (c instanceof CheckConstraint) {
			resetColumns((CheckConstraint) c);
		} else if (c instanceof ForeignKeyConstraint) {
			((ForeignKeyConstraint) c).validate();
		} else if (c instanceof ExcludeConstraint) {
			resetColumns((ExcludeConstraint) c);
		}
	}

	/**
	 * チェック制約のカラムを再設定します
	 * 
	 * @param cc
	 */
	protected void resetColumns(final CheckConstraint cc) {
		if (CommonUtils.size(cc.getColumns()) != 1) {
			cc.setColumns(new Column[0]);
		}
		final int size = cc.getColumns().length;
		if (this.getParent() != null) {
			final ColumnCollection columns = this.getParent().getColumns();
			for (int i = 0; i < size; i++) {
				final Column column = cc.getColumns()[i];
				final Column orgColumn = columns.get(column.getName());
				if (column != orgColumn) {
					cc.getColumns()[i] = orgColumn;
					if (orgColumn != null) {
						orgColumn.setCheckConstraint(cc);
					}
				}
			}
		}
	}

	/**
	 * 外部キー制約を追加します
	 * 
	 * @param fc 外部キー制約
	 */
	public void add(final ForeignKeyConstraint fc) {
		super.add(fc);
		if (this.getTable() == null) {
			return;
		}
		fc.validate();
		sort();
	}

	/**
	 * チェック制約を取得します
	 * 
	 */
	public List<CheckConstraint> getCheckConstraints() {
		return getCheckConstraints(c->true);
	}

	/**
	 * チェック制約を取得します
	 * 
	 */
	public List<CheckConstraint> getCheckConstraints(final Predicate<CheckConstraint> p) {
		final List<CheckConstraint> result = list(this.size());
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (!(c instanceof CheckConstraint)) {
				continue;
			}
			final CheckConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}

	/**
	 * ユニーク制約を取得します
	 * 
	 */
	public List<UniqueConstraint> getUniqueConstraints() {
		return getUniqueConstraints(c->true);
	}

	/**
	 * ユニーク制約を取得します
	 * 
	 */
	public List<UniqueConstraint> getUniqueConstraints(final Predicate<UniqueConstraint> p) {
		final List<UniqueConstraint> result = list(this.size());
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (!(c instanceof UniqueConstraint)) {
				continue;
			}
			final UniqueConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}
	
	/**
	 * 自テーブルのカラムに張られたユニーク制約を取得します
	 * @param columns 自テーブルのカラム
	 * @return 自テーブルのカラムに張られたユニーク制約
	 */
	public List<UniqueConstraint> getUniqueConstraints(final Column...columns) {
		final List<UniqueConstraint> list= getUniqueConstraints(c->true);
		final Map<String, Column> columnMap=CommonUtils.map();
		for(final Column column:columns){
			columnMap.put(column.getName(), column);
		}
		return list.stream().filter(con->{
			boolean match=true;
			for(final ReferenceColumn fkcol:con.getColumns()){
				final Column column=columnMap.get(con.getName());
				if (column==null){
					match=false;
					break;
				}
				if (!SchemaUtils.nameEquals(column, fkcol)){
					match=false;
					break;
				}
			}
			return match;
		}).collect(Collectors.toList());
	}

	/**
	 * 自テーブルのカラムに張られたユニーク制約を取得します
	 * @param columns 自テーブルのカラム
	 * @return 自テーブルのカラムに張られたユニーク制約
	 */
	public UniqueConstraint getUniqueConstraint(final Column...columns) {
		final List<UniqueConstraint> list= getUniqueConstraints(c->true);
		final Map<String, Column> columnMap=CommonUtils.map();
		for(final Column column:columns){
			columnMap.put(column.getName(), column);
		}
		return list.stream().filter(con->{
			if (columns.length!=con.getColumns().size()){
				return false;
			}
			boolean match=true;
			for(final ReferenceColumn fkcol:con.getColumns()){
				final Column column=columnMap.get(fkcol.getName());
				if (column==null){
					match=false;
					break;
				}
				if (!SchemaUtils.nameEquals(column, fkcol)){
					match=false;
					break;
				}
			}
			return match;
		}).findFirst().orElse(null);
	}

	/**
	 * 外部キー制約を取得します
	 * 
	 */
	public List<ForeignKeyConstraint> getForeignKeyConstraints() {
		return getForeinKeyConstraints(c->true);
	}

	/**
	 * 自テーブルのカラムに張られた外部キー制約を取得します
	 * @param columns 自テーブルのカラム
	 * @return 自テーブルのカラムに張られた外部キー制約
	 */
	public List<ForeignKeyConstraint> getForeignKeyConstraints(final Column...columns) {
		final List<ForeignKeyConstraint> list= getForeinKeyConstraints(c->true);
		final Map<String, Column> columnMap=CommonUtils.map();
		for(final Column column:columns){
			columnMap.put(column.getName(), column);
		}
		return list.stream().filter(fk->{
			boolean match=true;
			for(final Column fkcol:fk.getColumns()){
				final Column column=columnMap.get(fkcol.getName());
				if (column==null){
					match=false;
					break;
				}
				if (!SchemaUtils.nameEquals(column, fkcol)){
					match=false;
					break;
				}
			}
			return match;
		}).collect(Collectors.toList());
	}

	/**
	 * 指定したカラムを外部キーとしてもつ外部キー制約を取得します
	 * @param columns 自テーブルのカラム
	 * @return 指定したカラムを外部キーとしてもつ外部キー制約
	 */
	public ForeignKeyConstraint getForeignKeyConstraint(final Column...columns) {
		final List<ForeignKeyConstraint> list= getForeinKeyConstraints(c->true);
		final Map<String, Column> columnMap=CommonUtils.map();
		for(final Column column:columns){
			columnMap.put(column.getName(), column);
		}
		return list.stream().filter(fk->{
			if (columns.length!=fk.getColumns().length){
				return false;
			}
			boolean match=true;
			for(final Column fkcol:fk.getColumns()){
				final Column column=columnMap.get(fkcol.getName());
				if (column==null){
					match=false;
					break;
				}
				if (!SchemaUtils.nameEquals(column, fkcol)){
					match=false;
					break;
				}
			}
			return match;
		}).findFirst().orElse(null);
	}
	
	/**
	 * 外部キー制約を取得します
	 * 
	 */
	public List<ForeignKeyConstraint> getForeinKeyConstraints(final Predicate<ForeignKeyConstraint> p) {
		final List<ForeignKeyConstraint> result = list(this.size());
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (!(c instanceof ForeignKeyConstraint)) {
				continue;
			}
			final ForeignKeyConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}

	/**
	 * 排他制約を取得します
	 * 
	 */
	public List<ExcludeConstraint> getExcludeConstraints() {
		final List<ExcludeConstraint> result = list(this.size());
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (!(c instanceof ExcludeConstraint)) {
				continue;
			}
			final ExcludeConstraint cc = cast(c);
			result.add(cc);
		}
		return result;
	}
	
	/**
	 * 排他制約を取得します
	 * 
	 */
	public List<ExcludeConstraint> getExcludeConstraints(final Predicate<ExcludeConstraint> p) {
		final List<ExcludeConstraint> result = list(this.size());
		final int size = this.size();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (!(c instanceof ExcludeConstraint)) {
				continue;
			}
			final ExcludeConstraint cc = cast(c);
			if (p.test(cc)){
				result.add(cc);
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof ConstraintCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * Unique制約を除いてXML書き出します。
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	public void writeXml(final StaxWriter stax) throws XMLStreamException {
		stax.newLine();
		stax.indent();
		stax.writeStartElement(this.getSimpleName());
		stax.addIndentLevel(1);
		final int size = this.size();
		this.sort();
		for (int i = 0; i < size; i++) {
			final Constraint c = this.get(i);
			if (c instanceof UniqueConstraint) {
				final UniqueConstraint uc = (UniqueConstraint) c;
				if (uc.isPrimaryKey()) {
					uc.writeXmlAsPrimary(stax);
				} else {
					c.writeXml(stax);
				}
			} else {
				c.writeXml(stax);
			}
		}
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}

	public Table getTable() {
		return (Table) super.getParent();
	}

	/**
	 * プライマリキー制約を取得します
	 * 
	 */
	public UniqueConstraint getPrimaryKeyConstraint() {
		if (this.inner.size() == 0) {
			return null;
		}
		final Constraint obj = this.get(0);
		if (obj instanceof UniqueConstraint) {
			final UniqueConstraint uc = (UniqueConstraint) obj;
			if (uc.isPrimaryKey()) {
				return uc;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObjectCollection#validateAllElement()
	 */
	@Override
	protected void validateAllElement() {
		final int size = this.inner.size();
		for (int i = 0; i < size; i++) {
			final Constraint constraint = inner.get(i);
			if (constraint instanceof UniqueConstraint) {
				final UniqueConstraint uc = (UniqueConstraint) constraint;
				resetColumns(uc);
			} else if (constraint instanceof CheckConstraint) {
				final CheckConstraint cc = (CheckConstraint) constraint;
				resetColumns(cc);
			} else if (constraint instanceof ForeignKeyConstraint) {
				final ForeignKeyConstraint fc = (ForeignKeyConstraint) constraint;
				fc.validate();
			}
		}
		sort();
	}

	@Override
	public void sort() {
		Collections.sort(this.inner);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObjectCollection#find(com.sqlapp
	 * .data.schemas.AbstractNamedObject)
	 */
	@Override
	public Constraint find(final Constraint obj) {
		for(final Constraint con:this){
			if(con.like(obj)){
				return con;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}

	@Override
	protected ConstraintCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new ConstraintCollectionXmlReaderHandler();
	}
	
	@Override
	protected Supplier<Constraint> getElementSupplier() {
		return null;
	}

}
