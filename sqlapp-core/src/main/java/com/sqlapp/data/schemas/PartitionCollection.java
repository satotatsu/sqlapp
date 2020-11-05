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

/**
 * パーティションコレクション
 * 
 * @author satoh
 * 
 */
public final class PartitionCollection extends
		AbstractSchemaObjectCollection<Partition> implements
		HasParent<Partitioning>
	, NewElement<Partition, PartitionCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4116997309234290247L;

	/**
	 * コンストラクタ
	 */
	protected PartitionCollection() {
	}
	
	/**
	 * コンストラクタ
	 */
	protected PartitionCollection(Partitioning parent) {
		super(parent);
	}

	@Override
	protected Supplier<PartitionCollection> newInstance(){
		return ()->new PartitionCollection();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof PartitionCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractSchemaObjectList#clone()
	 */
	@Override
	public PartitionCollection clone() {
		return (PartitionCollection)super.clone();
	}

	@Override
	public Partitioning getParent() {
		return (Partitioning)super.getParent();
	}

	@Override
	public Partition newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Partition> getElementSupplier() {
		return ()->new Partition();
	}
}
