/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-test.
 *
 * sqlapp-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public abstract class AbstractTest {
	
	private Properties testProperties;

	protected AbstractTest(){
		try {
			testProperties=this.getProperties("test.properties");
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	protected String getTestProp(final String key){
		final String value=System.getProperty(key);
		if (value!=null){
			return value;
		}
		return testProperties.getProperty(key);
	}

	protected Properties getProperties(final String path) throws IOException{
		final Properties properties=new Properties();
		properties.load(ClassLoader.getSystemResourceAsStream(path));
		return properties;
	}

	protected String getResource(final String fileName) {
		final InputStream is = FileUtils.getInputStream(this.getClass(), fileName);
		final String sql = FileUtils.readText(is, "utf8");
		return sql;
	}
}
