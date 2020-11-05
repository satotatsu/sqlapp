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
package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.TripleKeyMap;

public abstract class RoutineReader<T extends ArgumentRoutine<? super T>>
		extends AbstractSchemaObjectReader<T> {

	protected RoutineReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<T> list) throws SQLException {
		TripleKeyMap<String, String, String, List<NamedArgument>> argumentMap;
		if (!list.isEmpty()){
			argumentMap=getObjectKeyMap(
					connection, setReaderParameter(getRoutineArgumentReader(), list));
		} else{
			argumentMap=new TripleKeyMap<String, String, String, List<NamedArgument>>();
		}
		for (T obj : list) {
			obj.setDialect(this.getDialect());
			String name = obj.getSpecificName() != null ? obj.getSpecificName()
					: obj.getName();
			List<NamedArgument> args = argumentMap.get(obj.getCatalogName(),
					obj.getSchemaName(), name);
			if (!isEmpty(args)) {
				obj.getArguments().addAll(args);
				argumentMap.remove(obj.getCatalogName(), obj.getSchemaName(),
						name);
			}
		}
	}

	protected TripleKeyMap<String, String, String, List<NamedArgument>> getObjectKeyMap(
			Connection connection, RoutineArgumentReader<?> reader) {
		if (reader == null) {
			return new TripleKeyMap<String, String, String, List<NamedArgument>>();
		}
		List<NamedArgument> ccList = reader.getAllFull(connection);
		TripleKeyMap<String, String, String, List<NamedArgument>> ccMap = CommonUtils
				.tripleKeyMap();
		for (NamedArgument argument : ccList) {
			String name = argument.getRoutine().getName();
			if (argument.getRoutine().getSpecificName() != null) {
				name = argument.getRoutine().getSpecificName();
			}
			List<NamedArgument> args = ccMap.get(argument.getCatalogName(),
					argument.getSchemaName(), name);
			if (args == null) {
				args = CommonUtils.list();
				ccMap.put(argument.getCatalogName(), argument.getSchemaName(),
						name, args);
			}
			args.add(argument);
		}
		return ccMap;
	}

	protected void setReaderParameter(RoutineArgumentReader<?> reader) {
		if (reader != null) {
			reader.setCatalogName(this.getCatalogName());
			SimpleBeanUtils
					.setValue(reader, "schemaName", this.getSchemaName());
			reader.setObjectName(this.getObjectName());
			initializeChild(reader);
		}
	}

	protected RoutineArgumentReader<?> setReaderParameter(RoutineArgumentReader<?> reader, List<T> list) {
		if (reader==null){
			return reader;
		}
		Set<String> schemaNames=CommonUtils.set();
		for(T obj:list){
			if (obj.getSchemaName()!=null){
				schemaNames.add(obj.getSchemaName());
			}
		}
		if (schemaNames.size()==1){
			reader.setSchemaName(CommonUtils.first(schemaNames));
		}
		return reader;
	}

	protected RoutineArgumentReader<?> getRoutineArgumentReader() {
		RoutineArgumentReader<?> reader = newRoutineArgumentReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract RoutineArgumentReader<?> newRoutineArgumentReader();

}
