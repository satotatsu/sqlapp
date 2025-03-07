/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * MaxDB
 * @author SATOH
 *
 */
public class MaxDb extends MySql {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6210411327886080024L;


    protected MaxDb(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * DB名
     */
    @Override
    public String getProductName() {
        return "maxdb";
    }

    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "maxdb";
    }
    
    @Override
    public boolean supportsIdentity() {
        return true;
    }
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.MySql#hashCode()
	 */
	@Override
    public int hashCode(){
    	return getProductName().hashCode();
    }
    /**
     * 同値判定
     */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}
}
