/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command;

import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;

public enum OutputFormatType {
	TSV(){
		@Override
		public String getSeparator(){
			return "\t";
		}
		@Override
		public WorkbookFileType getWorkbookFileType() {
			return WorkbookFileType.TSV;
		}
	},
	CSV(){
		@Override
		public String getSeparator(){
			return ",";
		}
		@Override
		public WorkbookFileType getWorkbookFileType() {
			return WorkbookFileType.CSV;
		}
	},
	FILE(){
		@Override
		public boolean isFile(){
			return true;
		}
	},
	TABLE(){
		@Override
		public boolean isTable(){
			return true;
		}
	},;
	
	public String getSeparator(){
		return null;
	}

	public WorkbookFileType getWorkbookFileType() {
		return null;
	}
	
	public boolean isFile(){
		return false;
	}

	public boolean isTable(){
		return false;
	}
	
	public static OutputFormatType parse(String text){
		if (text==null){
			return null;
		}
		text=text.toUpperCase();
		for(final OutputFormatType type:values()){
			if (text.endsWith(type.toString())){
				return type;
			}
		}
		return null;
	}
}