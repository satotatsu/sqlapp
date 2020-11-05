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
package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

public class DialectResolverTest {


	@Test
	public void testCompareTo() throws XMLStreamException, IOException {
		try{
			Connection connection=null;
			Dialect dialect=DialectResolver.getInstance().getDialect(connection);
			//Get all catalogs
			List<Catalog> catalogs=dialect.getCatalogReader().getAllFull(connection);
			//Get all schemas
			List<Schema> schemas=dialect.getCatalogReader().getSchemaReader().getAllFull(connection);
			schemas.forEach(s->{
				s.getTables().forEach(table->{
					System.out.println(table);
				});
			});
			Schema schema=schemas.get(0);
			schema.writeXml(new File("/tmp/schema.xml"));
			//Get all tables
			List<Table> tables=dialect.getCatalogReader().getSchemaReader().getTableReader().getAllFull(connection);
			tables.forEach(table->{
				//table partitioning info
				System.out.println(table.getPartitioning());
			});
		}catch(Exception e){
			
		}
	}

}
