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

module com.sqlapp.data.db.command {
	requires java.xml;
	requires java.sql;
	requires static lombok;
	requires transitive com.sqlapp.core;
	requires org.apache.logging.log4j;
	requires com.sqlapp.graphviz;
	exports com.sqlapp.data.db.command;
	exports com.sqlapp.data.db.command.export;
	exports com.sqlapp.data.db.command.html;
	exports com.sqlapp.data.db.command.version;
}