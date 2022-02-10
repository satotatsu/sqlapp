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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * DimensionHierarchyLevel
 * 
 * @author satoh
 * 
 */
public class DimensionHierarchyLevel extends AbstractNamedObject<DimensionHierarchyLevel>
		implements HasParent<DimensionHierarchyLevelCollection>, UnOrdered {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;
	
	private DimensionLevel level;
	
	protected DimensionHierarchyLevel() {
	}
	
	public DimensionHierarchyLevel(String name) {
		this.setName(name);
	}

	@Override
	protected Supplier<DimensionHierarchyLevel> newInstance(){
		return ()->new DimensionHierarchyLevel();
	}

	@Override
	protected String getSimpleName() {
		return "level";
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
		if (!(obj instanceof DimensionHierarchyLevel)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionHierarchyLevelCollection getParent() {
		return (DimensionHierarchyLevelCollection) super.getParent();
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
	public String getName() {
		if (this.level==null){
			return null;
		}
		return level.getName();
	}

	/**
	 * @param levelName the levelName to set
	 */
	@Override
	public DimensionHierarchyLevel setName(String levelName) {
		if (levelName==null){
			this.level=null;
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

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
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
	protected void toStringDetail(ToStringBuilder builder) {
	}

}
