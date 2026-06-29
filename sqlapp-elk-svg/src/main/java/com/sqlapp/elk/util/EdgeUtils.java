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

package com.sqlapp.elk.util;

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumnCollection;
import com.sqlapp.elk.TableSvgCreator;
import com.sqlapp.elk.schemas.TableNode;
import com.sqlapp.util.CommonUtils;

public class EdgeUtils {
	public static double calulucateY(TableNode tableNode, Column[] columns) {
		final List<Column> filteredColumns = tableNode.getColumns();
		if (CommonUtils.isEmpty(filteredColumns)) {
			return (TableSvgCreator.HEADER_HEIGHT + TableSvgCreator.ROW_BORDER_BOTTOM) / 2;
		}
		double sumY = 0;
		for (int i = 0; i < filteredColumns.size(); i++) {
			Column column = filteredColumns.get(i);
			int idx = filteredColumns.indexOf(column);
			if (idx >= 0) {
				sumY += caluculateByIndex(idx);
			}
		}
		return (sumY / filteredColumns.size());
	}

	private static double caluculateByIndex(int index) {
		return (TableSvgCreator.HEADER_HEIGHT)
				+ (index * (TableSvgCreator.ROW_HEIGHT + TableSvgCreator.ROW_BORDER_BOTTOM))
				+ ((TableSvgCreator.ROW_HEIGHT + TableSvgCreator.ROW_BORDER_BOTTOM) / 2.0);
	}

	public static double calulucateY(TableNode tableNode, ReferenceColumnCollection refColumns) {
		int columnSize = tableNode.getColumnSize();
		if (CommonUtils.isEmpty(columnSize)) {
			return TableSvgCreator.HEADER_HEIGHT;
		}
		Column[] columns = refColumns.stream().map(rc -> rc.getColumn()).toList().toArray(new Column[0]);
		return calulucateY(tableNode, columns);
	}
}
