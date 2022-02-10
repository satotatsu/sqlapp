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

import java.util.function.Consumer;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(fluent = true, chain=true) 
@Getter
@Setter
public class AbstractSchemaGraphBuilder {
	
	private DrawOptions drawOption=new DrawOptions();
	
	@Getter(lombok.AccessLevel.PROTECTED)
	@Setter(lombok.AccessLevel.PROTECTED)
	private AbstractSchemaGraphBuilder parent;
	
	protected <T extends AbstractSchemaGraphBuilder> T getRoot(){
		return getParent(this);
	}

	protected DrawOptions getDrawOption(){
		AbstractSchemaGraphBuilder root=this.getRoot();
		if (root!=this){
			return root.getDrawOption();
		}
		return drawOption;
	}

	@SuppressWarnings("unchecked")
	public <T extends AbstractSchemaGraphBuilder> T drawOption(Consumer<DrawOptions> cons) {
		cons.accept(this.getDrawOption());
		return (T)this;
	}
	
	@SuppressWarnings("unchecked")
	protected <T extends AbstractSchemaGraphBuilder> T getParent(AbstractSchemaGraphBuilder element){
		if (element.parent()==null){
			return (T)element;
		}
		return (T)getParent(element.parent());
	}

}
