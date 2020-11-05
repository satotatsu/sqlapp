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

import com.sqlapp.data.schemas.properties.object.DimensionHierarchyJoinKeysProperty;
import com.sqlapp.data.schemas.properties.object.DimensionHierarchyLevelsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension Hierarchy
 * 
 * @author satoh
 * 
 */
public class DimensionHierarchy extends AbstractSchemaObject<DimensionHierarchy>
		implements HasParent<DimensionHierarchyCollection>
	,DimensionHierarchyLevelsProperty<DimensionHierarchy>
	,DimensionHierarchyJoinKeysProperty<DimensionHierarchy>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;
	
	private DimensionHierarchyLevelCollection levels=new DimensionHierarchyLevelCollection(this);

	private DimensionHierarchyJoinKeyCollection joinKeys=new DimensionHierarchyJoinKeyCollection(this);
	
	public DimensionHierarchy() {

	}

	public DimensionHierarchy(String name) {
		super(name);
	}

	@Override
	protected Supplier<DimensionHierarchy> newInstance(){
		return ()->new DimensionHierarchy();
	}

	/**
	 * @param levels the levels to set
	 */
	protected DimensionHierarchy setLevels(DimensionHierarchyLevelCollection levels) {
		this.levels = levels;
		if (levels!=null){
			levels.setParent(this);
		}
		return instance();
	}

	/**
	 * @param levels the levels to set
	 */
	protected DimensionHierarchy setJoinKeys(DimensionHierarchyJoinKeyCollection joinKeys) {
		this.joinKeys = joinKeys;
		if (joinKeys!=null){
			joinKeys.setParent(this);
		}
		return instance();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.DIMENSION_HIERARCHY_LEVELS, this.getLevels());
		builder.add(SchemaObjectProperties.DIMENSION_HIERARCHY_JOIN_KEYS, this.getJoinKeys());
	}

	@Override
	protected String getSimpleName() {
		return "hierarchy";
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
		if (!(obj instanceof DimensionHierarchy)) {
			return false;
		}
		DimensionHierarchy val = (DimensionHierarchy) obj;
		if (!equals(SchemaObjectProperties.DIMENSION_HIERARCHY_LEVELS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.DIMENSION_HIERARCHY_JOIN_KEYS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionHierarchyCollection getParent() {
		return (DimensionHierarchyCollection) super.getParent();
	}

	/**
	 * @return the level
	 */
	public DimensionHierarchyLevelCollection getLevels() {
		return levels;
	}

	/**
	 * @return the joinKeys
	 */
	public DimensionHierarchyJoinKeyCollection getJoinKeys() {
		return joinKeys;
	}

	
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(this.getLevels())) {
			getLevels().writeXml(stax);
		}
		if (!isEmpty(this.getJoinKeys())) {
			getJoinKeys().writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
		this.getLevels().validate();
		this.getJoinKeys().validate();
	}
}
