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

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.EqualsUtils;

public abstract class AbstractDbObjectCollection<T extends AbstractDbObject<? super T>>
		extends AbstractBaseDbObjectCollection<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4540018510759477211L;

	/**
	 * コンストラクタ
	 */
	protected AbstractDbObjectCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected AbstractDbObjectCollection(DbCommonObject<?> parent) {
		super(parent);
	}
	
	@Override
	public AbstractDbObjectCollection<T> clone() {
		return (AbstractDbObjectCollection<T>)super.clone();
	}

	protected abstract Supplier<T> getElementSupplier();

	protected T newElementInternal(){
		Supplier<T> supplier=getElementSupplier();
		if (supplier!=null){
			T obj=supplier.get();
			setElementParent(obj);
			return obj;
		}
		return null;
	}

	protected boolean equals(ISchemaProperty props, T element1, T element2, EqualsHandler equalsHandler) {
		return equals(props.getLabel(), element1,element2, 
				props.getValue(element1), props.getValue(element2), equalsHandler);
	}

	protected boolean equals(String propertyName, T element1, T element2,
			Object value, Object targetValue, EqualsHandler equalsHandler) {
		return equalsHandler.valueEquals(propertyName, element1, element2, value,
				targetValue, EqualsUtils.getEqualsSupplier(value, targetValue));
	}
}
