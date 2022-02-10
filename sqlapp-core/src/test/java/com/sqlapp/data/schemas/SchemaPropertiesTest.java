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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.properties.AdminProperty;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.ClassFinder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;
/**
 * SchemaProperties
 * @author 竜夫
 *
 */
public class SchemaPropertiesTest {

	@Test
	public void testAll() throws ParseException {
		System.out.println("*********************************************************");
		final Table table=new Table();
		for(final ISchemaProperty prop:SchemaProperties.values()){
			System.out.println(prop+":"+prop.getLabel());
			testProperties(table, prop);
		}
		final Schema schema=new Schema();
		for(final ISchemaProperty prop:SchemaProperties.values()){
			testProperties(schema, prop);
		}
		final Column column=new Column();
		for(final ISchemaProperty prop:SchemaProperties.values()){
			testProperties(column, prop);
		}
	}

	@Test
	public void testDefinition() throws ParseException {
		System.out.println("*********************************************************");
		for(final SchemaProperties prop:SchemaProperties.values()){
			String name=prop.getPropertyClass().getSimpleName();
			name=name.substring(0, name.length()-"property".length());
			assertTrue(prop.toString().replace("_", "").equalsIgnoreCase(name), getMessage(prop));
			//
			final Method[] methods=prop.getPropertyClass().getMethods();
			for(final Method method:methods){
				if (Modifier.isStatic(method.getModifiers())){
					continue;
				}
				final String methodName=method.getName();
				if (methodName.startsWith("get")){
					assertTrue(prop.getLabel().equalsIgnoreCase(methodName.substring(3)), getMessage(prop));
				}else if(methodName.startsWith("is")||boolean.class.equals(method.getReturnType())){
					assertTrue(prop.getLabel().equalsIgnoreCase(methodName.substring(2)), getMessage(prop));
				}else if(methodName.startsWith("set")){
					if (SchemaProperties.NOT_NULL==prop&&method.getName().equals("setNullable")){
						continue;
					}
					assertTrue(prop.getLabel().equalsIgnoreCase(methodName.substring(3)), getMessage(prop));
				}
			}
		}
	}
	
	protected void testProperties(final Object obj, final ISchemaProperty prop) throws ParseException {
		if (!prop.isInstanceof(obj)){
			return;
		}
		if (prop==SchemaProperties.CATALOG_NAME){
			return;
		}else if (prop==SchemaProperties.SCHEMA_NAME){
			return;
		} else if (prop==SchemaProperties.DATA_TYPE_NAME){
			final boolean bool=prop.setValue(obj, prop.getLabel());
			if (bool){
				assertTrue(prop.getLabel().equalsIgnoreCase((String)prop.getValue(obj)), getMessage(obj, prop));
			}
		}else if (String.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, prop.getLabel());
			if (bool){
				final String val=(String)prop.getValue(obj);
				assertEquals(prop.getLabel(), val, getMessage(obj, prop));
			}
		}else if (boolean.class.equals(prop.getValueClass())){
			final Boolean defaultBool=(Boolean)prop.getValue(obj);
			if (obj instanceof Table&&prop==SchemaProperties.CASE_SENSITIVE){
				System.out.println(obj);
			}
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(defaultBool, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, "t");
				assertEquals(Boolean.TRUE, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(defaultBool, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, Boolean.TRUE);
				assertEquals(Boolean.TRUE, prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (Boolean.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, "t");
				assertEquals(Boolean.TRUE, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, Boolean.TRUE);
				assertEquals(Boolean.TRUE, prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (Integer.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, 1);
				assertEquals(Integer.valueOf(1), prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, 2);
				assertEquals(Integer.valueOf(2), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (int.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, 0);
			if (bool){
				assertEquals(Integer.valueOf(0), prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, 1);
				assertEquals(Integer.valueOf(1), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (Long.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, 1L);
				assertEquals(Long.valueOf(1), prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, 2L);
				assertEquals(Long.valueOf(2), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (BigInteger.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, BigInteger.valueOf(1));
				assertEquals(BigInteger.valueOf(1), prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, BigInteger.valueOf(2));
				assertEquals(BigInteger.valueOf(2), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (Timestamp.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				final Timestamp ts=DateUtils.toTimestamp("2017-01-23 10:13:40", "yyyy-MM-dd HH:mm:ss");
				prop.setValue(obj, ts);
				assertEquals(ts, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (List.class.isAssignableFrom(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, Collections.emptyList());
			if (bool){
				assertEquals(Collections.emptyList(), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (Set.class.isAssignableFrom(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, CommonUtils.linkedSet());
			if (bool){
				assertEquals(CommonUtils.linkedSet(), prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (byte[].class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, "a".getBytes());
			if (bool){
				assertTrue(Arrays.equals("a".getBytes(), (byte[])prop.getValue(obj)), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (prop.getValueClass().isEnum()){
			final Object enmValue=prop.getValueClass().getEnumConstants()[0];
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				if (enmValue instanceof CharacterSemantics&&SchemaProperties.DATA_TYPE.isInstanceof(obj)){
					SchemaProperties.DATA_TYPE.setValue(obj, Types.VARCHAR);
				}
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, enmValue.toString());
				assertEquals(enmValue, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, null);
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, enmValue);
				assertEquals(enmValue, prop.getValue(obj), getMessage(obj, prop));
			}
		}else if (DbInfo.class.equals(prop.getValueClass())){
			final boolean bool=prop.setValue(obj, null);
			if (bool){
				assertEquals(null, prop.getValue(obj), getMessage(obj, prop));
				prop.setValue(obj, new DbInfo());
				assertEquals(new DbInfo(), prop.getValue(obj), getMessage(obj, prop));
			}
		}else{
			throw new RuntimeException(getMessage(obj, prop));
		}
	}
	
	private String getMessage(final Object obj, final ISchemaProperty prop){
		return "property="+prop+", obj="+obj+", class="+obj.getClass().getSimpleName();
	}

	private String getMessage(final ISchemaProperty prop){
		return "property="+prop;
	}

	@Test
	public void testAllObjects() throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException {
		final ClassFinder finder=new ClassFinder();
		finder.setFilter(c->{
			if (Modifier.isAbstract(c.getModifiers())){
				return false;
			}
			if (!DbCommonObject.class.isAssignableFrom(c)){
				return false;
			}
			return true;
		});
		final List<Class<?>> classes=finder.find(Schema.class.getPackage().getName());
		for(final ISchemaProperty prop:SchemaProperties.values()){
			final List<Class<?>> dbObjectClasses=classes.stream().filter(c->{
				if (!prop.getPropertyClass().isAssignableFrom(c)){
					return false;
				}
				return true;
			}).collect(Collectors.toList());
 			for(final Class<?> dbObjectClass:dbObjectClasses){
 				final Constructor<?> constructor=dbObjectClass.getDeclaredConstructor();
 				constructor.setAccessible(true);
 				final Object object=constructor.newInstance();
 				if (prop.getLabel().startsWith("identity")&&prop.getValueClass()==boolean.class){
 					continue;
 				}
 				testProperties(object, prop);
 			}
		}
	}

	@Test
	public void testInterface(){
		final Set<Class<?>> clazzes=getPropertyIFs();
 		for(final ISchemaProperty props:SchemaProperties.values()){
 			assertTrue(clazzes.contains(props.getPropertyClass()), "props.getPropertyClass()="+props.getPropertyClass());
		}
	}
	
	private Set<Class<?>> getPropertyIFs(){
		final ClassFinder finder=new ClassFinder();
		finder.setFilter(c->{
			if (!c.isInterface()){
				return false;
			}
			if (!c.getSimpleName().endsWith("Property")){
				return false;
			}
			return true;
		});
		final List<Class<?>> clazzez=finder.find(AdminProperty.class.getPackage().getName());
		final Set<Class<?>> classSet=CommonUtils.linkedSet(clazzez);
		return classSet;
	}
	
}
