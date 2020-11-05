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
package com.sqlapp.data.db.command.version;

public enum Status {
	Pending(){
		@Override
		public boolean isPending(){
			return true;
		}
	},
	Started(){
		@Override
		public boolean isStarted(){
			return true;
		}
	},
	Completed(){
		@Override
		public boolean isCompleted(){
			return true;
		}
	},
	Errored(){
		@Override
		public boolean isErrord(){
			return true;
		}
	},
	;

	public boolean isPending(){
		return false;
	}
	
	public boolean isCompleted(){
		return false;
	}

	public boolean isStarted(){
		return false;
	}

	public boolean isErrord(){
		return false;
	}

	public static Status parse(String text){
		if (text==null||"".equals(text)){
			return Pending;
		}
		for(Status status:values()){
			if (status.toString().equalsIgnoreCase(text)){
				return status;
			}
		}
		return Pending;
	}
}
