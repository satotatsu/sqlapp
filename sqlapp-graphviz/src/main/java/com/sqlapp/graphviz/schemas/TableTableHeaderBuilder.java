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

import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.labeltable.Align;
import com.sqlapp.graphviz.labeltable.TdElement;
import com.sqlapp.graphviz.labeltable.TrElement;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.Function;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class TableTableHeaderBuilder extends AbstractSchemaGraphBuilder{

	private int cellSize=0;
	
	private String defaultColor=null;

	private String defaultBgcolor="#58ACFA";
	
	private Function<Table, String> name=(t)->t.getName();
	
	private Function<Table, String> color=(t)->this.defaultColor();

	private Function<Table, String> bgcolor=(t)->this.defaultBgcolor();

	private TableTableHeaderBuilder(){}
	
	
	public static TableTableHeaderBuilder create(){
		TableTableHeaderBuilder builder=new TableTableHeaderBuilder();
		return builder;
	}

	private int colspan=1;

	public int build(Table table, TrElement tr){
		createName(table, tr);
		return cellSize;
	}
	
	private TableTableHeaderBuilder createName(Table table, TrElement tr){
		String value=name.apply(table);
		if (value!=null){
			tr.addCell(cell->{
				setCommonAttribute(table, cell);
				cell.setAlign(Align.CENTER);
				cell.setPort(getPortName(table));
				cell.setValue(value);
				if (CommonUtils.isEmpty(table.getDisplayRemarks())){
					cell.setTooltip(table.getRemarks());
				} else{
					cell.setTooltip(table.getDisplayRemarks());
				}
			});
			addCellSize();
		}
		return instance();
	}

	
	private String getPortName(Table table){
		return SchemaGraphUtils.getName(table);
	}
	
	private TableTableHeaderBuilder setCommonAttribute(Table table, TdElement cell){
		cell.setAlign(Align.CENTER);
		//cell.setBorder(0);
		cell.setColor(getTableColor(table));
		cell.setBgcolor(getTableBgcolor(table));
		if (colspan>1){
			cell.setColspan(colspan);
		}
		return instance();
	}
	
	private TableTableHeaderBuilder instance(){
		return this;
	}
	
	private String getTableColor(Table table){
		String value=color.apply(table);
		if (value==null){
			return this.defaultColor();
		}
		return value;
	}

	private String getTableBgcolor(Table table){
		String value=bgcolor.apply(table);
		if (value==null){
			return this.defaultBgcolor();
		}
		return value;
	}

	private void addCellSize(){
		cellSize=cellSize+colspan;
	}

}
