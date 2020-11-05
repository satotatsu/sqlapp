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
package com.sqlapp.graphviz;

public enum Rankdir implements Default{
	TopToBottom("top to bottom"){
		@Override
		public boolean isDefault(){
			return true;
		}
		@Override
		public String toString(){
			return "TB";
		}
	}
	, LeftToRight("left to right"){
		@Override
		public String toString(){
			return "LR";
		}
	}
	, BottomToTop("bottom to top"){
		@Override
		public String toString(){
			return "BT";
		}
	}
	, RightToLeft("right to left"){
		@Override
		public String toString(){
			return "RL";
		}
	}
	,;

	private  final String comment;
	private Rankdir(String comment){
		this.comment=comment;
	}
	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}
	
}
