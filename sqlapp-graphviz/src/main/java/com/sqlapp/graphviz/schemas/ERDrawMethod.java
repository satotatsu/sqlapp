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
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.graphviz.ArrowType;
import com.sqlapp.graphviz.DirType;
import com.sqlapp.graphviz.Edge;
import com.sqlapp.graphviz.EdgeStyle;

public enum ERDrawMethod {
	IDEF1X(){
		@Override
		protected void drawDependent(ForeignKeyConstraint fk, Edge edge){
			edge.setDir(DirType.back);
			edge.setArrowhead(ArrowType.none);
			edge.setArrowtail(ArrowType.dot);
		}
		@Override
		protected void drawNoDependent(ForeignKeyConstraint fk, Edge edge){
			drawDependent(fk,edge);
			edge.setArrowhead(ArrowType.odiamond);
			edge.setStyle(EdgeStyle.dashed);
		}
	}, 
	IE(){
		@Override
		protected void drawDependent(ForeignKeyConstraint fk, Edge edge){
			edge.setDir(DirType.back);
			edge.setArrowhead(ArrowType.none, ArrowType.tee, ArrowType.tee);
			if (isPrimary(fk)){
				edge.setArrowtail(ArrowType.none, ArrowType.tee, ArrowType.odot);
			} else{
				edge.setArrowtail(ArrowType.crow, ArrowType.tee, ArrowType.odot);
			}
		}
		@Override
		protected void drawNoDependent(ForeignKeyConstraint fk, Edge edge){
			edge.setDir(DirType.back);
			edge.setArrowhead(ArrowType.none, ArrowType.odot);
			if (isPrimary(fk)){
				edge.setArrowtail(ArrowType.none, ArrowType.tee, ArrowType.odot);
			} else{
				edge.setArrowtail(ArrowType.crow, ArrowType.tee, ArrowType.odot);
			}
		}
	}, ;
	
	public void draw(ForeignKeyConstraint fk, Edge edge){
		if(isDependent(fk)){
			drawDependent(fk,edge);
		} else{
			drawNoDependent(fk,edge);
		}
	}

	protected void drawDependent(ForeignKeyConstraint fk, Edge edge){
		
	}

	protected void drawNoDependent(ForeignKeyConstraint fk, Edge edge){
		
	}


	public boolean isPrimary(ForeignKeyConstraint fk){
		for(Column column:fk.getColumns()){
			if (!column.isPrimaryKey()){
				return false;
			}
		}
		return true;
	}
	
	public boolean isDependent(ForeignKeyConstraint fk){
		for(Column column:fk.getColumns()){
			if (!column.isNotNull()){
				return false;
			}
		}
		return true;
	}
}
