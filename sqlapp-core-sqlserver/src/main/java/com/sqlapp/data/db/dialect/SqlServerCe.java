/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import java.util.function.Supplier;

import com.sqlapp.data.schemas.CascadeRule;

public class SqlServerCe extends SqlServer2005{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7304524420135145648L;

    protected SqlServerCe(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
    	super.registerDataType();
	}

	/**
	 * DB製品名
	 */
    public String getProductName(){
        return "SQLServerCE";
    }

    public boolean supportsWith(){
        return true;
    }
    /**
     * TOP句のサポート
     */
    public boolean supportsTop() {
        return true;
    }

    public boolean supportsRuleOnDelete(CascadeRule rule)
    {
        return true;
    }

    public boolean supportsRuleOnUpdate(CascadeRule rule)
    {
        return true;
    }
    public boolean supportsDefaultValueFunction()
    {
        return true;
    }
	@Override
    public int hashCode(){
    	return super.hashCode()+37;
    }

    /**
     * 同値判定
     */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}
}
