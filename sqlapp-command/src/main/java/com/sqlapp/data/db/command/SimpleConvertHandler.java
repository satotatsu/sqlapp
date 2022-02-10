/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.DbCommonObject;

/**
 * スキーマオブジェクトの変換用ハンドラー
 * 
 * @author tatsuo satoh
 * 
 */
public class SimpleConvertHandler implements ConvertHandler {

	@SuppressWarnings("rawtypes")
	private Function<DbCommonObject, DbCommonObject> converter=c->c;

	@SuppressWarnings("rawtypes")
	private Predicate<DbCommonObject> filter=c->true;

	public SimpleConvertHandler() {
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.command.IConvertHandler#handle(java.util.List)
	 */
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T extends DbCommonObject> List<T> handle(
			List<? extends DbCommonObject> list) {
		return (List<T>) list.stream().filter(getFilter()).map(getConverter()).collect(Collectors.toList());
	}

	@SuppressWarnings("rawtypes")
	protected Function<DbCommonObject, DbCommonObject> getConverter() {
		return converter;
	}

	@SuppressWarnings("rawtypes")
	public void setConverter(Function<DbCommonObject, DbCommonObject> converter) {
		this.converter = converter;
	}

	@SuppressWarnings("rawtypes")
	protected Predicate<DbCommonObject> getFilter() {
		return filter;
	}

	@SuppressWarnings("rawtypes")
	public void setFilter(Predicate<DbCommonObject> filter) {
		this.filter = filter;
	}

}
