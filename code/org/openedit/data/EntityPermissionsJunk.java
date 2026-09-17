package org.openedit.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class EntityPermissionsJunk
{
	private static final Log log = LogFactory.getLog(EntityPermissionsJunk.class);

	protected String fieldSettingsRole;
	
	public String getSettingsRole() {
		return fieldSettingsRole;
	}

	public void setSettingsRole(String inSettingsRole) {
		fieldSettingsRole = inSettingsRole;
	}

	Map fieldPermissions;
	
	public Map<String,Boolean> getPermissions()
	{
		if (fieldPermissions == null)
		{
			fieldPermissions = new HashMap();
			
		}

		return fieldPermissions;
	}

	public void putPermission(String inId, Object value)
	{
		if(value == null) 
		{
			//Don't include anything if the value isn't set at all in the database
			return;
		}
		getPermissions().put(inId, Boolean.valueOf(value.toString()));
	}

	public Boolean can(String inKey) 
	{
		if( getPermissions().isEmpty() )
		{
			return true;
		}
		
		Boolean can = getPermissions().get(inKey);
		if( can == null)
		{
			return false;
		}
		return can;
	}
	
	//TODO: Lazy load from DB
	

}
