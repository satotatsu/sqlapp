/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.graphviz.schemas;

import java.util.Locale;
import java.util.function.Predicate;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.function.ColumnPredicate;
import com.sqlapp.data.schemas.function.TablePredicate;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent=false, chain=true) 
@Getter
@Setter
@EqualsAndHashCode
public class DrawOptions {
	private ERDrawMethod erDrawMethod=ERDrawMethod.IE;

	private Predicate<Schema> schemaFilter=(schema)->true;

	private TablePredicate tableFilter=(table)->true;

	private TablePredicate inheritsTableFilter=(table)->true;

	private ColumnPredicate columnFilter=(column)->true;
	
	private boolean withRelationName=true;

	private boolean withRelationCascadeOption=true;

	private String font="Helvetica";

	private Double nodeFontsize=10d;

	private Double edgeFontsize=10d;

	private Locale locale=Locale.getDefault();
}
