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

/**
 * スキーマ比較用のデフォルトのハンドラー
 * @author 竜夫
 *
 */
public class DefaultSchemaEqualsHandler extends ExcludeFilterEqualsHandler{
	
	private static EqualsHandler equalsHandler=new DefaultSchemaEqualsHandler();
	
	public DefaultSchemaEqualsHandler(){
		super(
		SchemaProperties.CREATED_AT.getLabel(), SchemaProperties.LAST_ALTERED_AT.getLabel()
		, SchemaProperties.STATISTICS.getLabel()
		, SchemaProperties.DISPLAY_NAME.getLabel()
		, SchemaProperties.DISPLAY_REMARKS.getLabel()
		, SchemaProperties.VIRTUAL.getLabel()
		, SchemaProperties.OCTET_LENGTH.getLabel()
		, SchemaObjectProperties.ROWS.getLabel());
	}
	
	public static EqualsHandler getInstance(){
		return equalsHandler;
	}
	
	@Override
	public DefaultSchemaEqualsHandler clone(){
		return (DefaultSchemaEqualsHandler)super.clone();
	}
	
}
