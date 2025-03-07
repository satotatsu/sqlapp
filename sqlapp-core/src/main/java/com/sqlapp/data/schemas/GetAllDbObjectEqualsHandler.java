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

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

class GetAllDbObjectEqualsHandler extends EqualsHandler{

	private Predicate<DbObject<?>> predicate;

	private Consumer<DbObject<?>> consumer;
	
	GetAllDbObjectEqualsHandler(Consumer<DbObject<?>> consumer){
		this.consumer=consumer;
		this.predicate=(c)->true;
	}

	GetAllDbObjectEqualsHandler(Consumer<DbObject<?>> consumer, Predicate<DbObject<?>> predicate){
		this.consumer=consumer;
		this.predicate=predicate;
	}


	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.EqualsHandler#referenceEquals(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected boolean referenceEquals(Object object1, Object object2){
		if (object1 instanceof DbObject<?>){
			DbObject<?> val=(DbObject<?>)object1;
			if (predicate.test(val)){
				consumer.accept((DbObject<?>)val);
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.EqualsHandler#valueEquals(java.lang.String, java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object, java.util.function.BooleanSupplier)
	 */
	@Override
	protected boolean valueEquals(String propertyName, Object object1, Object object2,
			Object value1, Object value2, BooleanSupplier p){
		if (value1 instanceof DbObject<?>){
			DbObject<?> val=(DbObject<?>)value1;
			if (predicate.test(val)){
				consumer.accept((DbObject<?>)val);
			}
		} else if (value1 instanceof DbObjectCollection<?>){
			DbObjectCollection<?> c=(DbObjectCollection<?>)value1;
			c.stream().filter(val->predicate.test(val)).forEach(val->val.applyAll(consumer));
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.schemas.EqualsHandler#equalsResult(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected boolean equalsResult(Object object1, Object object2){
		return true;
	}
	
	@Override
	public GetAllDbObjectEqualsHandler clone(){
		return (GetAllDbObjectEqualsHandler)super.clone();
	}
}
