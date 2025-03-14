/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

module com.sqlapp.core {
	requires transitive java.xml;
	requires transitive java.sql;
	requires transitive org.apache.poi.poi;
	requires transitive org.apache.poi.scratchpad;
	requires transitive org.apache.poi.ooxml;
	requires transitive java.scripting;
	requires transitive java.desktop;
	requires transitive org.apache.logging.log4j.core;
	requires transitive org.apache.logging.log4j;
	requires transitive com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.dataformat.yaml;
	requires transitive com.fasterxml.jackson.databind;
	requires transitive io.github.javadiffutils;
	requires transitive org.locationtech.jts;
	requires transitive geolatte.geom;
	requires transitive mvel2;
	requires transitive univocity.parsers;
//	requires super.csv;
	requires static lombok;
	requires static com.zaxxer.hikari;

	exports com.sqlapp.data;
	exports com.sqlapp.data.converter;
	exports com.sqlapp.data.db.datatype;
//	exports com.sqlapp.data.db.dialect to com.sqlapp.core.db2mod, com.sqlapp.core.derby, com.sqlapp.core.firebird, com.sqlapp.core.h2mod, com.sqlapp.core.hirdb, com.sqlapp.core.hsql, com.sqlapp.core.informix, com.sqlapp.core.mariadb, com.sqlapp.core.mdb, com.sqlapp.core.mysql, com.sqlapp.core.phoenix, com.sqlapp.core.postgres, com.sqlapp.core.spanner, com.sqlapp.core.sqlite, com.sqlapp.core.sqlserver, com.sqlapp.core.sybase, com.sqlapp.core.symfoware, com.sqlapp.core.virtica;
	exports com.sqlapp.data.db.dialect;
//	exports com.sqlapp.data.db.dialect.jdbc.metadata  to com.sqlapp.core.db2mod, com.sqlapp.core.derby, com.sqlapp.core.firebird, com.sqlapp.core.h2mod, com.sqlapp.core.hirdb, com.sqlapp.core.hsql, com.sqlapp.core.informix, com.sqlapp.core.mariadb, com.sqlapp.core.mdb, com.sqlapp.core.mysql, com.sqlapp.core.phoenix, com.sqlapp.core.postgres, com.sqlapp.core.spanner, com.sqlapp.core.sqlite, com.sqlapp.core.sqlserver, com.sqlapp.core.sybase, com.sqlapp.core.symfoware, com.sqlapp.core.virtica;
	exports com.sqlapp.data.db.dialect.jdbc.metadata;
	exports com.sqlapp.data.db.dialect.resolver;
	exports com.sqlapp.data.db.dialect.information_schema.metadata;
	exports com.sqlapp.data.db.dialect.util;
	exports com.sqlapp.data.db.metadata;
	exports com.sqlapp.data.db.sql;
	exports com.sqlapp.data.geometry;
	exports com.sqlapp.data.interval;
	exports com.sqlapp.data.parameter;
	exports com.sqlapp.data.schemas;
	exports com.sqlapp.data.schemas.function;
	exports com.sqlapp.data.schemas.properties;
	exports com.sqlapp.data.schemas.properties.complex;
	exports com.sqlapp.data.schemas.properties.object;
	exports com.sqlapp.data.schemas.rowiterator;
	exports com.sqlapp.exceptions;
	exports com.sqlapp.jdbc;
	exports com.sqlapp.jdbc.sql;
	exports com.sqlapp.jdbc.sql.node;
	exports com.sqlapp.thread;
	exports com.sqlapp.util;
	exports com.sqlapp.util.eval;
	exports com.sqlapp.util.eval.mvel;
	exports com.sqlapp.util.eval.script;
	exports com.sqlapp.util.file;
	exports com.sqlapp.util.function;
	exports com.sqlapp.util.iterator;
	exports com.sqlapp.util.xml;
}