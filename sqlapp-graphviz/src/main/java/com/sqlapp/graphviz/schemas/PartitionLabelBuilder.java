/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.graphviz.schemas;

import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Statistics;
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
public class PartitionLabelBuilder extends AbstractSchemaGraphBuilder{

	private String defaultColor=null;

	private String defaultBgcolor="#EFFBFB";
	
	private Function<Partition, String> color=(t)->this.defaultColor();

	private Function<Partition, String> bgcolor=(t)->this.defaultBgcolor();
	
	private PartitionTableHeaderBuilder partitionTableHeaderBuilder=PartitionTableHeaderBuilder.create();
	
	private PartitionDetailCellBuilder partitionDetailCellBuilder=PartitionDetailCellBuilder.create();

	private BiConsumer<Partition, TableElement> setAttribute=null;
	
	private PartitionLabelBuilder(){}
	
	
	public static PartitionLabelBuilder create(){
		PartitionLabelBuilder builder=new PartitionLabelBuilder();
		return builder;
	}

	public static PartitionLabelBuilder createSimple(){
		PartitionLabelBuilder builder=new PartitionLabelBuilder();
		builder.partitionDetailCellBuilder=PartitionDetailCellBuilder.createSimple();
		return builder;
	}

	private int colspan=1;

	public void build(Partition partition, TableElement element){
		element.setCellpadding(0);
		element.setCellspacing(0);
		element.setBorder(0);
		element.setColor(this.color().apply(partition));
		element.setBgcolor(this.bgcolor().apply(partition));
		//Header
		element.addRow(tr->{
			tr.addCell(cell->{
				cell.setBorder(1);
				cell.setTable(tableElement->{
					tableElement.setCellpadding(1);
					tableElement.setCellspacing(0);
					tableElement.setBorder(0);
					tableElement.addRow(row->{
						partitionTableHeaderBuilder.parent(this);
						partitionTableHeaderBuilder.build(partition, row);
					});
				});
				
			});
		});
		if (partitionDetailCellBuilder!=null) {
			//Column
			element.addRow(tr->{
				tr.addCell(cell->{
					cell.setBorder(1);
					cell.setTable(tableElement->{
						tableElement.setCellpadding(1);
						tableElement.setCellspacing(0);
						tableElement.setBorder(0);
						tableElement.addRow(row->{
							partitionDetailCellBuilder.parent(this);
							partitionDetailCellBuilder.build(partition, row);
						});
					});
				});
			});
		}
		String rows=Statistics.ROWS.getFormatedValue(partition, this.getDrawOption().getLocale());
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
							td.setPort("footer_"+SchemaGraphUtils.getName(partition));
						});
						row.addCell(td->{
							td.setValue("");
						});
					});
				});
			});
		});
		if (setAttribute!=null){
			setAttribute.accept(partition, element);
		}
	}
	
	protected PartitionLabelBuilder instance(){
		return this;
	}

}
