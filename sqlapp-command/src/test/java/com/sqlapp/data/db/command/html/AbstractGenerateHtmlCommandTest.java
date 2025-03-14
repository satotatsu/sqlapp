/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.html;

import java.io.File;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.ISchemaProperty;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FileUtils;

public abstract class AbstractGenerateHtmlCommandTest {

	@Test
	public void testRun() {
		GenerateHtmlCommand command = new GenerateHtmlCommand();
		command.setCatalog(createCatalog());
		File tempDir = new File("bin/" + UUID.randomUUID().toString().replace("-", ""));
		tempDir.mkdirs();
		command.setOutputDirectory(tempDir);
		command.run();
		FileUtils.remove(tempDir);
	}

	protected Catalog createCatalog() {
		Catalog catalog = new Catalog();
		setValues(catalog);
		Schema schema = createSchema();
		catalog.getSchemas().add(schema);
		return catalog;
	}

	protected Schema createSchema() {
		Schema schema = new Schema("schema1");
		setValues(schema);
		return schema;
	}

	protected void setValues(DbCommonObject<?> obj) {
		for (ISchemaProperty prop : SchemaProperties.values()) {
			if (!prop.isInstanceof(obj)) {
				continue;
			}
			setValue(obj, prop);
		}
	}

	protected void setValue(DbCommonObject<?> obj, ISchemaProperty prop) {
		if (String.class.equals(prop.getValueClass())) {
			prop.setValue(obj, prop.getLabel());
		} else if (boolean.class.equals(prop.getValueClass())) {
			Boolean defaultBool = (Boolean) prop.getValue(obj);
			defaultBool = !defaultBool.booleanValue();
			prop.setValue(obj, defaultBool);
		} else if (Boolean.class.equals(prop.getValueClass())) {
			prop.setValue(obj, Boolean.TRUE);
		} else if (Integer.class.equals(prop.getValueClass())) {
			prop.setValue(obj, 1);
		} else if (int.class.equals(prop.getValueClass())) {
			prop.setValue(obj, 1);
		} else if (Long.class.equals(prop.getValueClass())) {
			prop.setValue(obj, 1L);
		} else if (long.class.equals(prop.getValueClass())) {
			prop.setValue(obj, 1L);
		} else if (Timestamp.class.equals(prop.getValueClass())) {
			Timestamp ts;
			try {
				ts = DateUtils.toTimestamp("2017-01-23 10:13:40", "yyyy-MM-dd HH:mm:ss");
				prop.setValue(obj, ts);
			} catch (ParseException e) {
			}
		} else if (List.class.isAssignableFrom(prop.getValueClass())) {
			prop.setValue(obj, stringList(3));
		} else if (Set.class.isAssignableFrom(prop.getValueClass())) {
			prop.setValue(obj, stringSet(3));
		} else if (byte[].class.equals(prop.getValueClass())) {
			prop.setValue(obj, "a".getBytes());
		} else if (prop.getValueClass().isEnum()) {
			Object enmValue = prop.getValueClass().getEnumConstants()[0];
			prop.setValue(obj, enmValue);
		} else if (DbInfo.class.equals(prop.getValueClass())) {
			DbInfo info = new DbInfo();
			prop.setValue(obj, info);
		}
	}

	private List<String> stringList(int len) {
		List<String> c = new ArrayList<>();
		for (int i = 0; i < len; i++) {
			c.add("line " + i);
		}
		return c;
	}

	private Set<String> stringSet(int len) {
		Set<String> c = new HashSet<>();
		for (int i = 0; i < len; i++) {
			c.add("line " + i);
		}
		return c;
	}

}
