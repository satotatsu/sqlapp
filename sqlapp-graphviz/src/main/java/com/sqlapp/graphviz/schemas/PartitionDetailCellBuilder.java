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

import com.sqlapp.data.schemas.Partition;
import com.sqlapp.graphviz.labeltable.Align;
import com.sqlapp.graphviz.labeltable.TdElement;
import com.sqlapp.graphviz.labeltable.TrElement;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.Function;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class PartitionDetailCellBuilder extends AbstractSchemaGraphBuilder{

	private String defaultColor=null;

	private String defaultBgcolor="#A9D0F5";
	
	private Function<Partition, String> name=(c)->c.getName();

	private Function<Partition, String> color=(c)->this.defaultColor();

	private Function<Partition, String> bgcolor=(c)->this.defaultBgcolor();

	private boolean createEmptyCell=true;
	
	
	private PartitionDetailCellBuilder(){}
	
	
	public static PartitionDetailCellBuilder create(){
		PartitionDetailCellBuilder builder=new PartitionDetailCellBuilder();
		return builder;
	}

	public static PartitionDetailCellBuilder createSimple(){
		PartitionDetailCellBuilder builder=new PartitionDetailCellBuilder();
		builder.createEmptyCell(false);
		builder.highValue(c->c.getLowValue());
		builder.lowValue(c->c.getHighValue());
		return builder;
	}

	private Function<Partition, String> highValue=(c)->{
		return c.getHighValue();
	};

	private Function<Partition, String> lowValue=(c)->{
		return c.getLowValue();
	};

	private int colspan=1;

	public void build(Partition partition, TrElement tr){
		createHead(partition, tr);
		createLowValue(partition, tr);
		createHighValue(partition, tr);
		createTail(partition, tr);
	}
	
	private PartitionDetailCellBuilder createHead(Partition partition, TrElement tr){
		tr.addCell(cell->{
			setCommonAttribute(partition, cell);
			cell.setValue("");
		});
		return instance();
	}
	
	private PartitionDetailCellBuilder createTail(Partition partition, TrElement tr){
		tr.addCell(cell->{
			setCommonAttribute(partition, cell);
			cell.setValue("");
		});
		return instance();
	}

	private void addEmptyCell(Partition partition, TrElement tr){
		if (this.createEmptyCell){
			tr.addCell(cell->{
				setCommonAttribute(partition, cell);
				cell.setValue("");
			});
		}
	}

	private PartitionDetailCellBuilder createLowValue(Partition partition, TrElement tr){
		String value=lowValue.apply(partition);
		if (value!=null){
			tr.addCell(cell->{
				setCommonAttribute(partition, cell);
				cell.setValue(value);
			});
		} else{
			addEmptyCell(partition, tr);
		}
		return instance();
	}

	private PartitionDetailCellBuilder createHighValue(Partition partition, TrElement tr){
		String value=highValue.apply(partition);
		if (value!=null){
			tr.addCell(cell->{
				setCommonAttribute(partition, cell);
				cell.setValue(value);
			});
		} else{
			addEmptyCell(partition, tr);
		}
		return instance();
	}

	private PartitionDetailCellBuilder setCommonAttribute(Partition partition, TdElement cell){
		cell.setAlign(Align.LEFT);
		cell.setColor(getPartitionColor(partition));
		cell.setBgcolor(getBgcolor(partition));
		return instance();
	}
	
	private PartitionDetailCellBuilder instance(){
		return this;
	}
	
	private String getPartitionColor(Partition partition){
		String value=color.apply(partition);
		if (value==null){
			return this.defaultColor();
		}
		return value;
	}

	private String getBgcolor(Partition partition){
		String value=bgcolor.apply(partition);
		if (value==null){
			return this.defaultBgcolor();
		}
		return value;
	}

}
