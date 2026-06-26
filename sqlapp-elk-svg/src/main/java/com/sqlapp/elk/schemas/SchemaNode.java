/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.schemas;

import java.util.List;

import org.eclipse.elk.graph.ElkNode;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.elk.NameMode;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;

@Getter
public class SchemaNode {

	private final Schema schema;
	private ElkNode rootNode;
	private ElkNode node;
	private final List<TableNode> tableNodes = CommonUtils.list();
	private double totalHeight;
	private double totalWidth;
	private NameMode nameMode = NameMode.NORMAL;
	private double minNameWidth = 24.0;

	public SchemaNode(Schema schema, ElkNode rootNode, ElkNode node) {
		this.schema = schema;
		this.rootNode = rootNode;
		this.node = node;
	}

	public void setNode(ElkNode node) {
		this.node = node;
	}

	public void setMinNameWidth(double minNameWidth) {
		this.minNameWidth = minNameWidth;
	}

	public void setNameMode(NameMode nameMode) {
		this.nameMode = nameMode;
	}

	public String getName() {
		return nameMode.getName(this.schema);
	}
}
