/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-elk-svg.
 *
 * sqlapp-elk-svg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sqlapp.elk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.elk.TableSvgCreator;
import com.sqlapp.elk.schemas.TableNode;

class EdgeUtilsTest {

	@Test
	void calculatePortAtCenterOfActualGridRow() {
		Table table = new Table("TEST_TABLE");
		for (int i = 0; i < 5; i++) {
			table.getColumns().add(new Column("COLUMN_" + i));
		}
		ElkNode rootNode = ElkGraphUtil.createGraph();
		TableNode tableNode = new TableNode(table, rootNode, ElkGraphUtil.createNode(rootNode));

		assertEquals(TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT / 2.0,
				EdgeUtils.calulucateY(tableNode, List.of(table.getColumns().get(0))));
		assertEquals(TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT * 4
				+ TableSvgCreator.ROW_HEIGHT / 2.0,
				EdgeUtils.calulucateY(tableNode, List.of(table.getColumns().get(4))));
	}

	@Test
	void resolveDetachedAndNameOnlyReferenceColumnsAgainstDisplayedTable() {
		Table table = new Table("TEST_TABLE");
		for (int i = 0; i < 5; i++) {
			table.getColumns().add(new Column("COLUMN_" + i));
		}
		ElkNode rootNode = ElkGraphUtil.createGraph();
		TableNode tableNode = new TableNode(table, rootNode, ElkGraphUtil.createNode(rootNode));
		double fifthRowCenter = TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_HEIGHT * 4
				+ TableSvgCreator.ROW_HEIGHT / 2.0;

		assertEquals(fifthRowCenter,
				EdgeUtils.calulucateY(tableNode, List.of(new Column("COLUMN_4"))));

		ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_TEST");
		foreignKey.getRelatedColumns().add("COLUMN_4");
		assertEquals(fifthRowCenter,
				EdgeUtils.calulucateY(tableNode, foreignKey.getRelatedColumns()));
	}
}
