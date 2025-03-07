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

package com.sqlapp.data.schemas.properties.complex;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.properties.SequenceNameProperty;
import com.sqlapp.data.schemas.properties.SequenceSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * Sequence IF
 * 
 * @author satoh
 * 
 */
public interface SequenceProperty<T extends DbCommonObject<?>> extends SequenceNameProperty<T>,SequenceSchemaNameProperty<T>{
	
	default Sequence getSequence(){
		Sequence obj= SimpleBeanUtils.getField(this, SchemaProperties.SEQUENCE_NAME.getLabel().replaceAll("Name", ""));
		if (obj != null && obj.getParent() == null) {
			setSequence(obj);
		}
		return obj;
	}

	@SuppressWarnings("unchecked")
	default T setSequence(Sequence value){
		if (this instanceof DbCommonObject){
			value=SchemaUtils.getSequenceFromParent(value, (DbCommonObject<?>)this);
		}
		SimpleBeanUtils.setField(this, SchemaProperties.SEQUENCE_NAME.getLabel().replaceAll("Name", ""), value);
		return (T)this;
	}

	@Override
	default String getSequenceSchemaName(){
		return getSequence()==null?null:getSequence().getSchemaName();
	}

	@Override
	default String getSequenceName(){
		return getSequence()==null?null:getSequence().getName();
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setSequenceName(String name) {
		if (CommonUtils.isEmpty(name)){
			this.setSequence(null);
		} else{
			if (this.getSequence()==null||!CommonUtils.eq(this.getSequenceName(), name)){
				Sequence obj=new Sequence(name);
				this.setSequence(obj);
			}
		}
		return (T)this;
	}

	@SuppressWarnings("unchecked")
	@Override
	default T setSequenceSchemaName(String name) {
		if (this.getSequence()==null||!CommonUtils.eq(this.getSequenceSchemaName(), name)){
			Sequence obj=new Sequence();
			obj.setSchemaName(name);
			this.setSequence(obj);
		}
		return (T)this;
	}

}
