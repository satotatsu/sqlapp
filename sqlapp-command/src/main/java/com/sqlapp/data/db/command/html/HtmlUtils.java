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
package com.sqlapp.data.db.command.html;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Base64.Encoder;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.Body;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.Operator;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.properties.ArrayDimensionProperties;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.SpecificNameProperty;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.JsonConverter;

import lombok.Data;

public class HtmlUtils {
	
	public static String objectFullName(Object obj){
		return objectFullNameInternal(obj, true, ".");
	}

	public static String objectFullNameWithoutSchemaName(Object obj){
		return objectFullNameInternal(obj, false, ".");
	}

	public static String objectFullPath(Object obj){
		return objectFullNameInternal(obj, true, "_");
	}
	
	public static String attr(String key, String value){
		if (value ==null|| value.length()==0){
			return "";
		}
		return key+"=\""+value+"\"";
	}

	private static String objectFullNameInternal(Object obj, boolean withSchema, String separator){
		if (obj instanceof String){
			return (String)obj;
		}
		StringBuilder builder=new StringBuilder();
		if (obj instanceof Schema){
			Schema schema=(Schema)obj;
			if (schema.getName()!=null){
				builder.append(schema.getName());
				return builder.toString();
			}
		} else if (obj instanceof Column){
			Column column=(Column)obj;
			if (withSchema){
				if (column.getSchemaName()!=null){
					builder.append(column.getSchemaName());
					builder.append(separator);
				}
			}
			builder.append(column.getTableName());
			builder.append(separator);
			builder.append(column.getName());
			return builder.toString();
		}else if (obj instanceof AbstractSchemaObject<?>){
			if (withSchema){
				AbstractSchemaObject<?> schemaObject=(AbstractSchemaObject<?>)obj;
				if (schemaObject.getSchemaName()!=null){
					builder.append(schemaObject.getSchemaName());
					builder.append(separator);
				}
			}
		}
		if (obj instanceof Operator){
			Operator namedObject=(Operator)obj;
			builder.append(escapeOperator(namedObject.getName()));
		}else if (obj instanceof com.sqlapp.data.schemas.ArgumentRoutine){
			ArgumentRoutine<?> namedObject=(ArgumentRoutine<?>)obj;
			if (namedObject.getSpecificName()!=null){
				builder.append(escapeName(namedObject.getSpecificName()));
			} else{
				builder.append(escapeName(namedObject.getName()));
			}
		}else if (obj instanceof AbstractNamedObject<?>){
			AbstractNamedObject<?> namedObject=(AbstractNamedObject<?>)obj;
			builder.append(escapeName(namedObject.getName()));
		}
		return builder.toString();
	}


	private static String escapeName(String value){
		if (value==null){
			return "";
		}
		StringBuilder builder=new StringBuilder(value.length());
		for(int i=0;i<value.length();i++) {
			char c=value.charAt(i);
			switch (c) {
			case '(':
			case ')':
			case '"':
			case ',':
			case '$':
			case '\\':
			case '/':
			case ':':
			case '*':
			case '?':
			case '<':
			case '>':
			case '|':
				builder.append('_');
				break;
			default:
				builder.append(c);
			}
		}
		return builder.toString();
	}

	private static Encoder encoder = Base64.getUrlEncoder();
	
	private static String escapeOperator(String value){
		try {
			return new String(encoder.encode(value.getBytes("utf8")));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String escape(Object obj){
		if (obj==null){
			return "";
		}
		if (obj instanceof byte[]){
			byte[] bytes=(byte[])obj;
			return "binary.length="+bytes.length;
		} else if (obj.getClass().isArray() || obj instanceof Collection) {
			StringBuilder builder = new StringBuilder();
			AbstractIterator<Object> itr = new AbstractIterator<Object>() {
				@Override
				protected void handle(Object obj, int index) throws Exception {
					if (index > 0) {
						builder.append(" ");
					}
					builder.append(escapeInternal(obj.toString()));
				}
			};
			try {
				itr.execute(obj);
			} catch (Exception e) {
			}
			return builder.toString();
		}
		return escapeInternal(obj.toString());
	}

	private static String escapeInternal(String text){
		if (text==null){
			return "";
		}
		if (text == null || text.length() == 0) {
			return text;
		}
		StringBuilder builder = new StringBuilder((int) (text.length() * 1.2));
		int size = text.codePointCount(0, text.length());
		for (int i = 0; i < size; i++) {
			int codePoint = text.codePointAt(i);
			if (codePoint == '<') {
				builder.append("&lt;");
			} else if (codePoint == '&') {
				builder.append("&amp;");
			} else if (codePoint == '>') {
				builder.append("&gt;");
			} else if (codePoint == '"') {
				builder.append("&quot;");
//			} else if (codePoint == '\'') {
//				builder.append("&apos;");
			} else if (codePoint == '\n') {
				builder.appendCodePoint(codePoint);
			} else if (codePoint == '\t') {
				builder.appendCodePoint(codePoint);
			} else if (Character.isISOControl(codePoint)) {
				//
			} else {
				builder.appendCodePoint(codePoint);
			}
		}
		return builder.toString();
	}
	
	public static String zeroToEmpty(Number obj){
		if (obj==null){
			return "";
		}
		Long val=Converters.getDefault().convertObject(obj, Long.class);
		if (val.longValue()==0){
			return "";
		}
		return obj.toString();
	}
	
	public static String escapeHtml(Object obj){
		if (obj==null){
			return "";
		}
		return obj.toString();
	}
	
	public static String joinLines(Object obj){
		if (obj instanceof String){
			return (String)obj;
		}
		if (obj ==null){
			return "";
		}
		StringBuilder builder=new StringBuilder();
		AbstractIterator<String> itr=new AbstractIterator<String>(){

			@Override
			protected void handle(String obj, int index) throws Exception {
				builder.append(obj);
				builder.append('\n');
			}
		};
		try {
			itr.execute(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return builder.toString();
	}
	
	
	public static boolean isAssemblySource(String name){
		if (name.endsWith(".cs")){
			return true;
		}
		if (name.endsWith(".vb")){
			return true;
		}
		if (name.endsWith(".jsl")){
			return true;
		}
		return false;
	}
	
	public static String binaryToString(byte[] bytes, String encode) throws UnsupportedEncodingException{
		if (CommonUtils.isEmpty(bytes)){
			return "";
		}
		return new String(bytes, encode);
	}
	
	private static Map<String,Integer> countProperties(Object arg){
		Collection<?> list=(Collection<?>)arg;
		Map<String,Integer> countMap=new IntegerLinkedHashMap();
		for(Object obj:list){
			if (obj instanceof DbObject){
				DbObject<?> dbObject=DbObject.class.cast(obj);
				Map<String,Object> map=dbObject.toMap();
				map.forEach((k,v)->{
					int val=getCount(countMap, k+"Count");
					val=val+countObject(v);
					countMap.put(k+"Count", val);
				});
			}
		}
		countSpecificName(list, countMap);
		countArrayDimensionInfo(list, countMap);
		countSchemaName(list, countMap);
		return countMap;
	}

	@SuppressWarnings("serial")
	static class IntegerLinkedHashMap extends LinkedHashMap<String, Integer>{
		@Override
		public boolean containsKey(Object obj){
			return true;
		}

		@Override
		public Integer get(Object obj){
			Integer val=super.get(obj);
			if (val==null){
				return 0;
			}
			return val;
		}
	}
	
	
	public static Map<String,Integer> countBodyProperties(Collection<?> collection){
		Collection<?> list=collection.stream().filter(c->(c instanceof Body)).map(c->((Body<?>)c).getBody()).filter(c->c!=null).collect(Collectors.toList());
		Map<String,Integer> countMap=new IntegerLinkedHashMap();
		for(Object obj:list){
			if (obj instanceof DbObject){
				DbObject<?> dbObject=DbObject.class.cast(obj);
				Map<String,Object> map=dbObject.toMap();
				map.forEach((k,v)->{
					int val=getCount(countMap, k+"BodyCount");
					val=val+countObject(v);
					countMap.put(k+"BodyCount", val);
				});
			}
		}
		countSpecificName("specificNameBodyCount", list, countMap);
		return countMap;
	}

	private static int getCount(Map<String,Integer> map, String key){
		Integer val=map.get(key);
		if (val==null){
			return 0;
		}
		return val.intValue();
	}
	
	private static int countObject(Object obj){
		if (obj==null){
			return 0;
		}else if (obj instanceof Boolean){
			if (((Boolean)obj).booleanValue()){
				return 1;
			} else{
				return 0;
			}
		}else if (obj instanceof String){
			if (!CommonUtils.isEmpty(((String)obj))){
				return 1;
			} else{
				return 0;
			}
		}else if (obj instanceof Number){
			Number num=Number.class.cast(obj);
			if (num.longValue()!=0L){
				return 1;
			} else{
				return 0;
			}
		}else if (obj instanceof Collection){
			return ((Collection<?>)obj).size();
		}
		return 1;
	}
	
	public static Map<String,Integer> getArrayDimensionInfo(Collection<?> list){
		Map<String,Integer> map=CommonUtils.linkedMap();
		countArrayDimensionInfo(list, map);
		return map;
	}

	private static void countSchemaName(Collection<?> list, Map<String,Integer> map){
		String name=SchemaProperties.SCHEMA_NAME.getLabel()+"Count";
		map.remove(name);
		for(Object obj:list){
			if (obj instanceof SchemaNameProperty){
				SchemaNameProperty<?> props=SchemaNameProperty.class.cast(obj);
				if (!CommonUtils.isEmpty(props.getSchemaName())){
					int val=getCount(map, name);
					val++;
					map.put(name, val);
				}
			}
		}
	}

	private static void countArrayDimensionInfo(Collection<?> list, Map<String,Integer> map){
		String arrayDimensionCount=SchemaProperties.ARRAY_DIMENSION.getLabel()+"Count";
		map.remove(arrayDimensionCount);
		String arrayLowerBoundCount=SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND+"Count";
		map.remove(arrayLowerBoundCount);
		String arrayUpperBoundCount=SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND+"Count";
		map.remove(arrayUpperBoundCount);
		for(Object obj:list){
			if (obj instanceof ArrayDimensionProperties){
				ArrayDimensionProperties<?> props=ArrayDimensionProperties.class.cast(obj);
				if (props.getArrayDimension()>0){
					String name=arrayDimensionCount;
					int val=getCount(map, name);
					val++;
					map.put(name, val);
					if (props.getArrayDimensionLowerBound()>0){
						name=arrayLowerBoundCount;
						val=getCount(map, name);
						val++;
						map.put(name, val);
					}
					if (props.getArrayDimensionLowerBound()>0){
						name=arrayUpperBoundCount;
						val=getCount(map, name);
						val++;
						map.put(name, val);
					}
				}
			}
		}
	}
	
	private static void countSpecificName(Collection<?> c, Map<String,Integer> map){
		String name="specificNameCount";
		countSpecificName(name, c, map);
	}
	
	private static void countSpecificName(String name, Collection<?> c, Map<String,Integer> map){
		map.remove(name);
		for(Object obj:c){
			if (obj instanceof SpecificNameProperty){
				SpecificNameProperty<?> props=SpecificNameProperty.class.cast(obj);
				if (obj instanceof NameProperty){
					NameProperty<?> nameProps=NameProperty.class.cast(obj);
					if (!CommonUtils.eq(props.getSpecificName(), nameProps.getName())){
						int val=getCount(map, name);
						val++;
						map.put(name, val);
					}
				} else{
					if (!CommonUtils.isEmpty(props.getSpecificName())){
						int val=getCount(map, name);
						val++;
						map.put(name, val);
					}
				}
			}
		}
	}

	private static JsonConverter jsonConverter=new JsonConverter();
	
	public static String toJson(Object obj){
		if (obj==null){
			return null;
		}
		return jsonConverter.toJsonString(obj);
	}
	
	@SuppressWarnings("unchecked")
	public static List<? extends AbstractPartition<?>> getSubPartitions(Object obj){
		if (obj instanceof Partition){
			return ((Partition)obj).getSubPartitions();
		} else if (obj instanceof Partitioning){
			return getSubPartitions((Partitioning)obj);
		} else if (obj instanceof Collection){
			return getSubPartitions((Collection<Partition>)obj);
		}
		return null;
	}

	private static List<SubPartition> getSubPartitions(Collection<Partition> partitions){
		List<SubPartition> result=partitions.stream().flatMap(p->p.getSubPartitions().stream()).collect(Collectors.toList());
		return result;
	}

	
	@Data
	public static class DbValueInfo{
		private Set<String> values=CommonUtils.treeSet();
		private boolean booleanValue=false;
		
	}

	public static ParametersContext analyzeAllProperties(List<?> list){
		ParametersContext context=new ParametersContext();
		Map<String,Integer> map=HtmlUtils.countProperties(list);
		map.forEach((k,v)->{
			context.put(k, v);
		});
		map=HtmlUtils.countBodyProperties(list);
		map.forEach((k,v)->{
			context.put(k, v);
		});
		putContextParam(SchemaProperties.SPECIFICS, list, context);
		putContextParam(SchemaProperties.STATISTICS, list, context);
		putContextBodyParam(SchemaProperties.SPECIFICS, list, context);
		putContextBodyParam(SchemaProperties.STATISTICS, list, context);
		map=HtmlUtils.getArrayDimensionInfo(list);
		map.forEach((k,v)->{
			context.put(k, v);
		});
		context.put("arrayDimensionSize", map.size());
		return context;
	}
	
	
	private static void putContextParam(ISchemaProperty prop, List<?> list, ParametersContext context){
		context.put( prop.getLabel(), getMapValues(prop, c->c,list));
	}

	private static void putContextBodyParam(ISchemaProperty prop, List<?> list, ParametersContext context){
		context.put(prop.getLabel()+"Body", getMapValues(prop, c->{
			if (c instanceof Body){
				return ((Body<?>)c).getBody();
			} else{
				return null;
			}
		},list));
	}
	
	private static Map<String,DbValueInfo> getMapValues(ISchemaProperty prop, Function<Object,Object> func, List<?> list){
		Map<String,DbValueInfo> result=CommonUtils.treeMap();
		for(Object obj:list){
			Object converted=func.apply(obj);
			if (converted==null){
				continue;
			}
			if (prop.isInstanceof(converted)){
				@SuppressWarnings("unchecked")
				Map<String,String> map=(Map<String,String>)prop.getValue(converted);
				map.forEach((k,v)->{
					DbValueInfo dbValueInfo=result.get(k);
					if (dbValueInfo==null){
						dbValueInfo=new DbValueInfo();
						result.put(k, dbValueInfo);
					}
					if (v!=null){
						dbValueInfo.getValues().add(v);
					}
				});
			}
		}
		return result;
	}

	public static String colspan(Object obj){
		if (obj==null){
			return "";
		}
		if (obj instanceof Number){
			int val=((Number)obj).intValue();
			if (val<=1){
				return "";
			}
			return "colspan=\""+val+"\"";
		}
		return "";
	}

	public static String rowspan(Object obj){
		if (obj==null){
			return "";
		}
		if (obj instanceof Number){
			int val=((Number)obj).intValue();
			if (val<=1){
				return "";
			}
			return "rowspan=\""+val+"\"";
		}
		return "";
	}
	
	public static String partitionRange(Object obj){
		if (obj==null){
			return "";
		}
		if (obj instanceof Table) {
			Table table=Table.class.cast(obj);
			if (table.getPartitionParent()==null) {
				return "";
			}
			Table parentTable=table.getPartitionParent().getTable();
			if (parentTable.getPartitioning()==null) {
				return "";
			}
			String value;
			if (parentTable.getPartitioning().getPartitioningType()==null) {
				value=PartitioningType.Range.toExpression(table);
			} else {
				value=parentTable.getPartitioning().getPartitioningType().toExpression(table);
			}
			if (value!=null) {
				return value;
			}
		}
		return "";
	}
	
	public static boolean schemaExists(Object schema ,Object obj){
		if (obj==null){
			return true;
		}
		SchemaCollection schemas=null;
		if (obj instanceof AbstractDbObjectCollection){
			Catalog catalog=((AbstractDbObjectCollection<?>)obj).getAncestor(Catalog.class);
			if (catalog!=null){
				schemas=catalog.getSchemas();
			}
			if (schema==null){
				schemas=((AbstractDbObjectCollection<?>)obj).getAncestor(SchemaCollection.class);
			}
		} else{
			Catalog catalog=((AbstractDbObject<?>)obj).getAncestor(Catalog.class);
			if (catalog!=null){
				schemas=catalog.getSchemas();
			}
			if (schema==null){
				schemas=((AbstractDbObject<?>)obj).getAncestor(SchemaCollection.class);
			}
		}
		if (schemas==null){
			return true;
		}
		if (schema instanceof String){
			Schema sc=schemas.get((String)schema);
			return sc!=null;
		} if (schema instanceof Schema){
			Schema sc=schemas.get(((Schema)schema).getName());
			return sc!=null;
		}
		Schema sc=schemas.get(((SchemaNameProperty<?>)schema).getSchemaName());
		return sc!=null;
	}
	
	public static String getProductInfo(DbCommonObject<?> obj){
		return SchemaUtils.getProductInfo(obj);
	}
}
