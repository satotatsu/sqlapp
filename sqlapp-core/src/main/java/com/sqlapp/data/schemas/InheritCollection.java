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

import static com.sqlapp.util.CommonUtils.eq;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxWriter;

/**
 * 継承元Tableのコレクション
 * 
 */
public final class InheritCollection extends
		AbstractSchemaObjectCollection<Table> implements HasParent<Table> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -9212319248422513405L;

	/**
	 * コンストラクタ
	 */
	protected InheritCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected InheritCollection(Table derived) {
		super(derived);
	}

	@Override
	protected Supplier<InheritCollection> newInstance(){
		return ()->new InheritCollection();
	}
	
	/**
	 * スキーマを取得します
	 * 
	 */
	@Override
	public Schema getSchema() {
		return getParent().getSchema();
	}

	@Override
	protected void renew() {
		if (getParent()!=null&&getParent().getParent() != null) {
			for (int i = 0; i < inner.size(); i++) {
				Table table = inner.get(i);
				Table getTable = null;
				if (table.getSchemaName() != null
						&& !eq(table.getSchemaName(), getParent()
								.getSchemaName())) {
					if (getParent().getSchema() != null
							&& getParent().getSchema().getParent() != null) {
						Schema schema = getParent().getSchema().getParent()
								.get(table.getSchemaName());
						if (schema != null) {
							getTable = schema.getTable(table.getName());
						}
					}
				} else {
					getTable = getParent().getParent().get(table.getName());
				}
				if (getTable != null) {
					inner.set(i, getTable);
				}
			}
		}
		super.renew();
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof InheritCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * 要素が等しいかを判定します
	 * 
	 * @param t1
	 * @param t2
	 * @param equalsHandler
	 */
	@Override
	protected boolean equalsElement(Table t1, Table t2,
			EqualsHandler equalsHandler) {
		if (!equals(SchemaProperties.TABLE_NAME, t1, t2, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SCHEMA_NAME, t1, t2, equalsHandler)) {
			return false;
		}
		return true;
	}

	@Override
	public InheritCollection clone() {
		InheritCollection clone=new InheritCollection();
		for(Table table:this){
			clone.add(table.clone());
		}
		return clone;
	}

	/**
	 * XML書き出し
	 * 
	 * @param name
	 *            書き出す要素名
	 * @param stax
	 * @throws XMLStreamException
	 */
	public void writeXml(String name, StaxWriter stax)
			throws XMLStreamException {
		int size = this.size();
		stax.newLine();
		stax.indent();
		stax.writeStartElement(name);
		stax.addIndentLevel(1);
		for (int i = 0; i < size; i++) {
			Table obj = this.get(i);
			boolean bool = !eq(getParent().getSchemaName(), obj.getSchemaName());
			obj.writeSimpleXml(stax, bool);
		}
		stax.addIndentLevel(-1);
		stax.newLine();
		stax.indent();
		stax.writeEndElement();
	}

	@Override
	public Table getParent() {
		return (Table)super.getParent();
	}

	/**
	 * スキーマ情報の初期化
	 * 
	 * @param e
	 */
	@Override
	protected void initializeSchemaInfo(Table e) {
	}

	@Override
	protected void setElementParent(Table e) {
	}

	@Override
	protected Supplier<Table> getElementSupplier() {
		return null;
	}
}
