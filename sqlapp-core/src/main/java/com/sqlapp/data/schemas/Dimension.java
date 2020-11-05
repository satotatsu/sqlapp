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

import com.sqlapp.data.schemas.properties.object.DimensionAttributesProperty;
import com.sqlapp.data.schemas.properties.object.DimensionHierarchiesProperty;
import com.sqlapp.data.schemas.properties.object.DimensionLevelsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension
 * 
 * @author satoh
 * 
 */
public final class Dimension extends AbstractSchemaObject<Dimension> implements
		HasParent<DimensionCollection>
	, DimensionLevelsProperty<Dimension>
	, DimensionHierarchiesProperty<Dimension>
	, DimensionAttributesProperty<Dimension>
	{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 */
	protected Dimension() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Dimension(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Dimension> newInstance(){
		return ()->new Dimension();
	}
	
	/**
	 * DimensionLevelコレクション
	 */
	private DimensionLevelCollection levels = new DimensionLevelCollection(this);
	
	/** DimensionHierarchyCollectionの変数名 */
	private DimensionHierarchyCollection hierarchies=new DimensionHierarchyCollection(this);
	/** DimensionAttributeCollectionの変数名 */
	private DimensionAttributeCollection attributes=new DimensionAttributeCollection(this);
	/**
	 * 新規にDimensionLevelを作成します
	 * 
	 * @param name
	 */
	public DimensionLevel newLevel(String name) {
		DimensionLevel level = new DimensionLevel(name);
		level.setParent(levels);
		return level;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Dimension)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Dimension val = (Dimension) obj;
		if (!equals(SchemaObjectProperties.DIMENSION_LEVELS, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.DIMENSION_HIERARCHIES, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.DIMENSION_ATTRIBUTES, val,
				equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	@Override
	public DimensionCollection getParent() {
		return (DimensionCollection) super.getParent();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		if (!isEmpty(getLevels())) {
			builder.add(SchemaObjectProperties.DIMENSION_LEVELS, this.getLevels());
		}
		if (!isEmpty(getHierarchies())) {
			builder.add(SchemaObjectProperties.DIMENSION_HIERARCHIES, this.getHierarchies());
		}
		if (!isEmpty(getAttributes())) {
			builder.add(SchemaObjectProperties.DIMENSION_ATTRIBUTES, this.getAttributes());
		}
	}

	/**
	 * @return the levels
	 */
	@Override
	public DimensionLevelCollection getLevels() {
		return levels;
	}

	/**
	 * @return the hierarchies
	 */
	@Override
	public DimensionHierarchyCollection getHierarchies() {
		return hierarchies;
	}

	/**
	 * @return the attributes
	 */
	@Override
	public DimensionAttributeCollection getAttributes() {
		return attributes;
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
		if (!isEmpty(this.getHierarchies())) {
			getHierarchies().writeXml(stax);
		}
		if (!isEmpty(this.getAttributes())) {
			getAttributes().writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}

	protected Dimension setLevels(DimensionLevelCollection levels){
		this.levels=levels;
		if (levels!=null){
			levels.setParent(this);
		}
		return instance();
	}
	
	protected Dimension setHierarchies(DimensionHierarchyCollection hierarchies){
		this.hierarchies=hierarchies;
		if (hierarchies!=null){
			hierarchies.setParent(this);
		}
		return instance();
	}

	protected Dimension setAttributes(DimensionAttributeCollection attributes){
		this.attributes=attributes;
		if (attributes!=null){
			attributes.setParent(this);
		}
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		levels.validate();
		hierarchies.validate();
		attributes.validate();
	}

}
