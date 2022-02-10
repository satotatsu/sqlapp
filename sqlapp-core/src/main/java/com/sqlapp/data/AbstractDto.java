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

package com.sqlapp.data;

import java.io.Serializable;

import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * DTO共通抽象クラス
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractDto implements Serializable,Cloneable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder();
		buildToString(builder);
		return builder.toString();
	}

	protected abstract void buildToString(ToStringBuilder builder);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final HashCodeBuilder builder = new HashCodeBuilder();
		buildHashCode(builder);
		return builder.hashCode();
	}

	protected abstract void buildHashCode(HashCodeBuilder builder);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AbstractDto)) {
			return false;
		}
		return true;
	}
	
	@Override
	public AbstractDto clone(){
		try {
			return (AbstractDto)super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new InternalError(e);
		}
	}
}
