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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.LevelNameProperty;
import com.sqlapp.data.schemas.properties.object.DimensionHierarchyJoinKeyColumnsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension Hierarchy
 * 
 * @author satoh
 * 
 */
public class DimensionHierarchyJoinKey extends AbstractDbObject<DimensionHierarchyJoinKey>
		implements HasParent<DimensionHierarchyJoinKeyCollection>
	, LevelNameProperty<DimensionHierarchyJoinKey>
	, DimensionHierarchyJoinKeyColumnsProperty<DimensionLevel>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;
	
	private DimensionLevel level;

	private DimensionHierarchyJoinKeyColumnCollection columns=new DimensionHierarchyJoinKeyColumnCollection(this);
	
	public DimensionHierarchyJoinKey() {

	}
	
	@Override
	protected Supplier<DimensionHierarchyJoinKey> newInstance(){
		return ()->new DimensionHierarchyJoinKey();
	}

	/**
	 * @param columns the columns to set
	 */
	protected DimensionHierarchyJoinKey setColumns(DimensionHierarchyJoinKeyColumnCollection columns) {
		this.columns = columns;
		if (columns!=null){
			columns.setParent(this);
		}
		return instance();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		builder.add(SchemaProperties.LEVEL_NAME, this.getLevelName());
		builder.add(SchemaObjectProperties.DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS, this.getColumns().toString("(", ")"));
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(this.getColumns())) {
			getColumns().writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}
	
	@Override
	protected String getSimpleName() {
		return "joinKey";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof DimensionHierarchyJoinKey)) {
			return false;
		}
		DimensionHierarchyJoinKey val = (DimensionHierarchyJoinKey) obj;
		if (!equals(SchemaProperties.LEVEL_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.DIMENSION_HIERARCHY_JOIN_KEY_COLUMNS, val,
				equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionHierarchyJoinKeyCollection getParent() {
		return (DimensionHierarchyJoinKeyCollection) super.getParent();
	}

	/**
	 * @return the level
	 */
	public DimensionLevel getLevel() {
		return level;
	}
	
	/**
	 * @return the level
	 */
	@Override
	public String getLevelName() {
		if (this.level==null){
			return null;
		}
		return level.getName();
	}

	/**
	 * @param level the level to set
	 */
	public DimensionHierarchyJoinKey setLevel(DimensionLevel level) {
		this.level = level;
		return this.instance();
	}

	/**
	 * @param levelName the levelName to set
	 */
	@Override
	public DimensionHierarchyJoinKey setLevelName(String levelName) {
		if (levelName==null){
			this.level=null;
			return this.instance();
		}
		if (this.level==null){
			DimensionLevel level=new DimensionLevel(levelName);
			this.level=level;
		} else{
			if (!CommonUtils.eq(this.level.getName(), levelName)){
				DimensionLevel level=new DimensionLevel(levelName);
				this.level=level;
			}
		}
		return this.instance();
	}

	/**
	 * @return the columns
	 */
	@Override
	public DimensionHierarchyJoinKeyColumnCollection getColumns() {
		return columns;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.LEVEL_NAME.getLabel(), this.getLevelName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
		Dimension dimension=this.getAncestor(Dimension.class);
		if (dimension==null){
			return;
		}
		if (this.getLevel()==null){
			return;
		}
		DimensionLevel level=dimension.getLevels().get(this.getLevel().getName());
		if (level!=null){
			this.level=level;
		}
	}

	@Override
	public int compareTo(DimensionHierarchyJoinKey o) {
		return 0;
	}


}
