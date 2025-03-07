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
public class PartitionTableHeaderBuilder extends AbstractSchemaGraphBuilder{

	private int cellSize=0;
	
	private String defaultColor=null;

	private String defaultBgcolor="#58ACFA";
	
	private Function<Partition, String> name=(t)->t.getName();
	
	private Function<Partition, String> color=(t)->this.defaultColor();

	private Function<Partition, String> bgcolor=(t)->this.defaultBgcolor();

	private PartitionTableHeaderBuilder(){}
	
	
	public static PartitionTableHeaderBuilder create(){
		PartitionTableHeaderBuilder builder=new PartitionTableHeaderBuilder();
		return builder;
	}

	private int colspan=1;

	public int build(Partition partition, TrElement tr){
		createName(partition, tr);
		return cellSize;
	}
	
	private PartitionTableHeaderBuilder createName(Partition partition, TrElement tr){
		String value=name.apply(partition);
		if (value!=null){
			tr.addCell(cell->{
				setCommonAttribute(partition, cell);
				cell.setAlign(Align.CENTER);
				cell.setPort(getPortName(partition));
				cell.setValue(value);
				if (CommonUtils.isEmpty(partition.getDisplayRemarks())){
					cell.setTooltip(partition.getRemarks());
				} else{
					cell.setTooltip(partition.getDisplayRemarks());
				}
			});
			addCellSize();
		}
		return instance();
	}

	
	private String getPortName(Partition partition){
		return SchemaGraphUtils.getName(partition);
	}
	
	private PartitionTableHeaderBuilder setCommonAttribute(Partition partition, TdElement cell){
		cell.setAlign(Align.CENTER);
		//cell.setBorder(0);
		cell.setColor(getTableColor(partition));
		cell.setBgcolor(getTableBgcolor(partition));
		if (colspan>1){
			cell.setColspan(colspan);
		}
		return instance();
	}
	
	private PartitionTableHeaderBuilder instance(){
		return this;
	}
	
	private String getTableColor(Partition partition){
		String value=color.apply(partition);
		if (value==null){
			return this.defaultColor();
		}
		return value;
	}

	private String getTableBgcolor(Partition partition){
		String value=bgcolor.apply(partition);
		if (value==null){
			return this.defaultBgcolor();
		}
		return value;
	}

	private void addCellSize(){
		cellSize=cellSize+colspan;
	}

}
