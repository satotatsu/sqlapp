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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DefaultProperty;
import com.sqlapp.data.schemas.properties.complex.PartitionFunctionProperty;
import com.sqlapp.data.schemas.properties.object.ReferenceTableSpacesProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * パーティションスキームに対応したオブジェクト(SQLServer2005以降専用)
 * 
 * @author satoh
 * 
 */
public final class PartitionScheme extends AbstractNamedObject<PartitionScheme>
		implements HasParent<PartitionSchemeCollection>,DefaultProperty<PartitionScheme>
	, PartitionFunctionProperty<PartitionScheme>
	,ReferenceTableSpacesProperty<PartitionScheme>{
	/**  serialVersionUID */
	private static final long serialVersionUID = 1L;

	public PartitionScheme() {
	}

	public PartitionScheme(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<PartitionScheme> newInstance(){
		return ()->new PartitionScheme();
	}

	/** ファイルグループ名 */
	private ReferenceTableSpaceCollection tableSpaces = new ReferenceTableSpaceCollection(this);
	/** パーティション関数 */
	@SuppressWarnings("unused")
	private PartitionFunction partitionFunction = null;
	/** デフォルト */
	private boolean _default = (Boolean)SchemaProperties.DEFAULT.getDefaultValue();

	@Override
	public ReferenceTableSpaceCollection getTableSpaces() {
		return tableSpaces;
	}

	public PartitionScheme setTableSpaces(String... args) {
		this.tableSpaces.clear();
		if (args!=null){
			for(String arg:args){
				this.tableSpaces.add(new TableSpace(arg));
			}
		}
		return this;
	}
	
	protected PartitionScheme setTableSpaces(ReferenceTableSpaceCollection tableSpaces) {
		this.tableSpaces=tableSpaces;
		if (tableSpaces!=null){
			tableSpaces.setParent(this);
		}
		return instance();
	}

	@Override
	public boolean isDefault() {
		return _default;
	}

	@Override
	public PartitionScheme setDefault(boolean _default) {
		this._default = _default;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof PartitionScheme)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		PartitionScheme val = (PartitionScheme) obj;
		if (!equals(SchemaObjectProperties.REFERENCE_TABLE_SPACES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PARTITION_FUNCTION_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.PARTITION_FUNCTION_NAME.getLabel(),
				this.getPartitionFunctionName());
		stax.writeAttribute(SchemaProperties.DEFAULT.getLabel(), this.isDefault());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(this.getTableSpaces())) {
			this.getTableSpaces().writeXml(SchemaObjectProperties.REFERENCE_TABLE_SPACES.getLabel(), stax);
		}
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.REFERENCE_TABLE_SPACES.getLabel(), this.getTableSpaces());
		builder.add(SchemaProperties.PARTITION_FUNCTION_NAME, this.getPartitionFunctionName());
		builder.add(SchemaProperties.DEFAULT, this.isDefault());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public PartitionSchemeCollection getParent() {
		return (PartitionSchemeCollection) super.getParent();
	}

	@Override
	protected void validate(){
		super.validate();
		ReferenceTableSpaceCollection list=new ReferenceTableSpaceCollection(this);
		Catalog catalog=this.getAncestor(Catalog.class);
		if (catalog==null){
			return;
		}
		for(TableSpace tableSpace:this.tableSpaces){
			TableSpace ts=catalog.getTableSpaces().get(tableSpace.getName());
			if (ts!=null){
				list.add(ts);
			} else{
				list.add(tableSpace);
			}
		}
		this.tableSpaces=list;
	}
}
