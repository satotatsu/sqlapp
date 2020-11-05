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

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Statistics;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.graphviz.labeltable.TableElement;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class TableLabelBuilder extends AbstractSchemaGraphBuilder{

	private String defaultColor=null;

	private String defaultBgcolor="#EFFBFB";
	
	private Function<Table, String> color=(t)->this.defaultColor();

	private Function<Table, String> bgcolor=(t)->this.defaultBgcolor();
	
	private TableTableHeaderBuilder tableTableHeaderBuilder=TableTableHeaderBuilder.create();
	
	private TableColumnCellBuilder tableColumnCellBuilder=TableColumnCellBuilder.create();

	private BiConsumer<Table, TableElement> setAttribute=null;
	
	private TableLabelBuilder(){}
	
	
	public static TableLabelBuilder create(){
		TableLabelBuilder builder=new TableLabelBuilder();
		return builder;
	}

	public static TableLabelBuilder createSimple(){
		TableLabelBuilder builder=new TableLabelBuilder();
		builder.tableColumnCellBuilder=TableColumnCellBuilder.createSimple();
		return builder;
	}

	private int colspan=1;

	public void build(Table table, TableElement element){
		element.setCellpadding(0);
		element.setCellspacing(0);
		element.setBorder(0);
		element.setColor(this.color().apply(table));
		element.setBgcolor(this.bgcolor().apply(table));
		//Header
		if (tableTableHeaderBuilder!=null) {
			element.addRow(tr->{
				tr.addCell(cell->{
					cell.setBorder(1);
					cell.setTable(tableElement->{
						tableElement.setCellpadding(1);
						tableElement.setCellspacing(0);
						tableElement.setBorder(0);
						tableElement.addRow(row->{
							tableTableHeaderBuilder.parent(this);
							tableTableHeaderBuilder.build(table, row);
						});
					});
					
				});
			});
		}
		if (tableColumnCellBuilder!=null) {
			//Column
			if (table.getColumns().size()>0){
				element.addRow(tr->{
					tr.addCell(cell->{
						cell.setBorder(1);
						cell.setTable(tableElement->{
							tableElement.setCellpadding(1);
							tableElement.setCellspacing(0);
							tableElement.setBorder(0);
							for(Column column:table.getColumns()){
								if(this.getDrawOption().getColumnFilter().test(column)){
									tableElement.addRow(row->{
										tableColumnCellBuilder.parent(this);
										tableColumnCellBuilder.build(column, row);
									});
								}
							}
						});
					});
				});
			}
		}
		String rows=Statistics.ROWS.getFormatedValue(table, this.getDrawOption().getLocale());
		if (!CommonUtils.isEmpty(rows)) {
			element.addRow(tr->{
				tr.addCell(cell->{
					cell.setBorder(1);
					cell.setTable(tableElement->{
						tableElement.setCellpadding(1);
						tableElement.setCellspacing(0);
						tableElement.setBorder(0);
						tableElement.addRow(row->{
							row.addCell(td->{
								td.setValue(rows+" rows");
							});
						});
					});
				});
			});
		}
		//Dummy
		if (table.getColumns().size()>0){
			element.addRow(tr->{
				tr.addCell(cell->{
					cell.setBorder(0);
					cell.setTable(tableElement->{
						tableElement.setCellpadding(0);
						tableElement.setCellspacing(0);
						tableElement.setBorder(0);
						tableElement.addRow(row->{
							row.addCell(td->{
								td.setValue("");
							});
							row.addCell(td->{
								td.setValue("");
								td.setPort("footer_"+SchemaGraphUtils.getName(table));
							});
							row.addCell(td->{
								td.setValue("");
							});
						});
					});
				});
			});
		}
		if (setAttribute!=null){
			setAttribute.accept(table, element);
		}
	}
	
	protected TableLabelBuilder instance(){
		return this;
	}

}
