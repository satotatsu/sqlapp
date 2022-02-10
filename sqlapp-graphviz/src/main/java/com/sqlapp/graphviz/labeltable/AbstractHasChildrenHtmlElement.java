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

package com.sqlapp.graphviz.labeltable;

import java.util.List;
import java.util.function.Consumer;

import com.sqlapp.util.CommonUtils;

public abstract class AbstractHasChildrenHtmlElement<T extends AbstractHasChildrenHtmlElement<?>> extends AbstractHtmlElement{
	
	private List<AbstractHtmlElement> children=CommonUtils.list();
	
	protected List<AbstractHtmlElement> getChildren(){
		return this.children;
	}

	protected void clearChildren(){
		this.children.forEach(c->{
			c.setParent(null);
		});
		this.children.clear();
	}

	
	protected void appenChild(AbstractHtmlElement element){
		this.getChildren().add(element);
		element.setParent(this);
	}
	
	public T setValue(String value){
		CharactersElement element=new CharactersElement(value);
		clearChildren();
		appenChild(element);
		return instance();
	}
	
	public T addCharacters(String value){
		CharactersElement element=new CharactersElement(value);
		appenChild(element);
		return instance();
	}

	public T addFont(Consumer<FontElement> c){
		FontElement element=new FontElement();
		appenChild(element);
		c.accept(element);
		return instance();
	}

	public T addBr(Consumer<BrElement> c){
		BrElement element=new BrElement();
		appenChild(element);
		c.accept(element);
		return instance();
	}

	public T addBr(){
		BrElement element=new BrElement();
		appenChild(element);
		return instance();
	}

	public T addImg(Consumer<ImgElement> c){
		ImgElement element=new ImgElement();
		appenChild(element);
		c.accept(element);
		return instance();
	}
	
	public T addVr(Consumer<VrElement> c){
		VrElement element=new VrElement();
		appenChild(element);
		c.accept(element);
		return instance();
	}
	
	public T addHr(Consumer<HrElement> c){
		HrElement element=new HrElement();
		appenChild(element);
		c.accept(element);
		return instance();
	}

	@SuppressWarnings("unchecked")
	protected T instance(){
		return (T)this;
	}

}
