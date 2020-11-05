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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.ToStringBuilder;

public class DbObjectsTest {

	@Test
	public void test() {
		System.out.println("********************************************************************");
		for(DbObjects enm:DbObjects.values()){
			if(enm.isCollection()){
				assertTrue(List.class.isAssignableFrom(enm.getType()));
			} else{
				if (enm.getParentType()!=null&&!enm.getCamelCase().contains("Constraint")){
					assertEquals(SchemaUtils.getPluralName(enm.getCamelCase()), enm.getParentType().getCamelCase());
				}
			}
		}
	}

	@Test
	public void testCatalog() {
		Set<ISchemaProperty> props=SchemaUtils.getSchemaObjectProperties(Catalog.class);
		for(ISchemaProperty prop:props){
			boolean find=false;
			for(DbObjects enm:DbObjects.values()){
				if (prop.getValueClass().equals(enm.getType())){
					find=true;
					break;
				}
			}
			assertTrue(find, "prop="+prop.getLabel());
		}
	}
	
	@Test
	public void testSchema() {
		Set<ISchemaProperty> props=SchemaUtils.getSchemaObjectProperties(Schema.class);
		for(ISchemaProperty prop:props){
			boolean find=false;
			for(DbObjects enm:DbObjects.values()){
				if (prop.getValueClass().equals(enm.getType())){
					find=true;
					break;
				}
			}
			assertTrue(find, "prop="+prop.getLabel());
		}
	}

	private void print(DbObjects enm){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("text", enm.toString());
		builder.add("camelCase", enm.getCamelCase());
		builder.add("snakeCase", enm.getSnakeCase());
		builder.add("depends", enm.getDepends());
		System.out.println(builder.toString());
	}
	
	@Test
	public void testCreateOrder() {
		System.out.println("********************************************************************");
		System.out.println("********************************create order************************************");
		List<DbObjects> createOrders=DbObjects.getCreateOrders();
		for(DbObjects enm:createOrders){
			print(enm);
		}
	}
	
	@Test
	public void testDropOrder() {
		System.out.println("********************************************************************");
		System.out.println("********************************drop order************************************");
		List<DbObjects> createOrders=DbObjects.getDropOrders();
		for(DbObjects enm:createOrders){
			print(enm);
		}
	}
	
}
