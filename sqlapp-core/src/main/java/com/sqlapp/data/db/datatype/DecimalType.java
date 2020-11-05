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
package com.sqlapp.data.db.datatype;

/**
 * DECIMALを表す型
 * @author satoh
 *
 */
public class DecimalType extends AbstractPrecisionScaleType<DecimalType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンストラクタ
	 */
	public DecimalType(){
		this(DataType.DECIMAL.getTypeName());
	}

	/**
	 * コンストラクタ
	 */
	public DecimalType(String dataTypeName){
		this.setDataType(DataType.DECIMAL);
		initialize(dataTypeName);
		setDefaultValueLiteral("0");
	}
	
	@Override
	public DecimalType setMaxPrecision(Integer maxPrecision) {
		super.setMaxPrecision(maxPrecision);
		if (this.getMaxScale()==null){
			this.setMaxScale(maxPrecision);
		}
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#hashCode()
	 */
	@Override
	public int hashCode(){
		return super.hashCode();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof DecimalType)){
			return false;
		}
		return true;
	}
}
