/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.split;
import static com.sqlapp.util.CommonUtils.unwrap;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * Postgres8.4 Function reader
 * 
 * @author satoh
 * 
 */
public class Postgres84FunctionReader extends PostgresFunctionReader {

	protected Postgres84FunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Function createFunction(ExResultSet rs)
			throws SQLException {
		Function obj = super.createFunction(rs);
		if(this.getReaderOptions().isReadDefinition()){
			obj.setDefinition(rs.getString("functiondef"));
		}
		Boolean proisagg=this.getBoolean(rs, "proisagg");
		if (proisagg!=null&&proisagg.booleanValue()){
			obj.setFunctionType(FunctionType.Aggregate);
		}
		Boolean proiswindow=this.getBoolean(rs, "proiswindow");
		if (proiswindow!=null&&proiswindow.booleanValue()){
			obj.setFunctionType(FunctionType.Window);
		}
		Boolean proretset=this.getBoolean(rs, "proretset");
		if (proretset!=null&&proretset.booleanValue()){
			obj.setFunctionType(FunctionType.Table);
		}
		String function_result=rs.getString("function_result");
		if (function_result!=null&&function_result.startsWith("TABLE(")){
			setReturningRecordType(rs, obj);
		}
		return obj;
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("functions84.sql");
		return node;
	}

	protected void setReturningRecordType(ExResultSet rs, Function obj) throws SQLException{
		String function_result=rs.getString("function_result");
		function_result=function_result.substring(6,function_result.length()-1);
		String[] args=function_result.split(",");
		obj.getReturning().toTable();
		for(String arg:args){
			arg=CommonUtils.trim(arg);
			if (CommonUtils.isEmpty(arg)){
				continue;
			}
			int pos=arg.indexOf(' ');
			String name;
			String dataTypeName;
			if (pos>=0){
				name=CommonUtils.trim(arg.substring(0, pos));
				dataTypeName=CommonUtils.trim(arg.substring(pos));
			} else{
				name=null;
				dataTypeName=arg;
			}
			obj.getReturning().getTable().getColumns().add(name, c->{
				c.setDataTypeName(dataTypeName);
			});
		}
		obj.setFunctionType(FunctionType.Table);
	}

	@Override
	protected void setArguments(ExResultSet rs, Function obj) throws SQLException{
		String function_arguments=rs.getString("function_arguments");
		String function_identity_arguments=rs.getString("function_identity_arguments");
		String[] args=function_arguments.split(",");
		String[] argWithoutDefaults=function_identity_arguments.split(",");
		String allArgNames = unwrap(rs.getString("proargnames"), "{", "}");
		String[] argNameArray = split(allArgNames, "[, ]");
		SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
		for(int i=0;i<args.length;i++){
			String arg=argWithoutDefaults[i];
			String argDefault=args[i];
			String defaultValue=getDefaultValue(argDefault, arg);
			String argName=null;
			if (!CommonUtils.isEmpty(argNameArray)){
				argName=CommonUtils.trim(argNameArray[i]);
			}
			arg=CommonUtils.trim(arg);
			if (CommonUtils.isEmpty(arg)){
				continue;
			}
			boolean[] variadic=new boolean[]{false};
			ParameterDirection[] diretion=new ParameterDirection[]{ParameterDirection.Input};
			if (arg.startsWith("INOUT")){
				diretion[0]=ParameterDirection.Inout;
				arg=CommonUtils.trim(arg.substring(5));
			}else if (arg.startsWith("OUT")){
				diretion[0]=ParameterDirection.Output;
				arg=CommonUtils.trim(arg.substring(3));
				if (arg.startsWith("VARIADIC")){
					arg=CommonUtils.trim(arg.substring(8));
					variadic[0]=true;
				}
			}else if (arg.startsWith("IN ")){
				arg=CommonUtils.trim(arg.substring(3));
			}
			if (!CommonUtils.isEmpty(argName)){
				arg=CommonUtils.trim(arg.substring(argName.length()));
			}
			String dataTypeName=arg;
			obj.getArguments().add(CommonUtils.trim(argName), argument->{
				argument.setDataTypeName(dataTypeName);
				argument.setDirection(diretion[0]);
				argument.setDefaultValue(defaultValue);
				setVariadic(variadic[0], argument);
				builder.add(argument.getDataTypeName()!=null?argument.getDataTypeName():argument.getDataType().toString());
			});
		}
		obj.setSpecificName(obj.getName() + "(" + builder.toString()+ ")");
	}
	
	private String getDefaultValue(String arg, String argWithoutDefault){
		if (arg.length()==argWithoutDefault.length()){
			return null;
		}
		return CommonUtils.trim(arg.substring(argWithoutDefault.length()));
	}
	
	private void setVariadic(boolean variadic, NamedArgument argument){
		if (variadic){
			argument.getSpecifics().put("VARIADIC", variadic);
		}
	}
}
