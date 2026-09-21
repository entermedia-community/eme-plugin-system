package org.openedit.users;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.data.QueryBuilder;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.hittracker.HitTracker;
import org.openedit.profile.UserProfile;

public class Permissions implements CatalogEnabled 
{
	private static final Log log = LogFactory.getLog(Permissions.class);

	protected UserProfile fieldUserProfile;
	protected Set fieldSystemRolePermissions;
	protected SearcherManager fieldSearcherManager;
	protected String fieldCatalogId;
	
	public Permissions()
	{
	}
	
	//system wide
	
	public String getCatalogId()
	{
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId)
	{
		fieldCatalogId = inCatalogId;
	}

	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}

	public Set getSystemRolePermissions()
	{
		return fieldSystemRolePermissions;  ///Default system permissions from the default entity?
	}

	public void setSystemRolePermissions(Set inSettingsRolePermissions)
	{
		fieldSystemRolePermissions = inSettingsRolePermissions;
	}
	

	public UserProfile getUserProfile()
	{
		return fieldUserProfile;
	}


	public void setUserProfile(UserProfile inUserProfile)
	{
		fieldUserProfile = inUserProfile;
	}


	public Permissions(UserProfile inProfile)
	{
		setUserProfile(inProfile)	;
	}

	//"product","createnew"
//	public Boolean can(Data module, Data inData, String inKey)
//	{
//		//First check role
//		
//		
////		String role = findEntityPermissionLevel(module);
////		if( isDataOwner )
////		{
////			boolean can = can("owner" + inModuleId,inKey);
////			if( can )
////			{
////				return true;
////			}
////		}	
//		boolean can = can(module,inKey);
//		return can;
//	}

	private Data loadModule(String inModuleId)
	{
		Data  module = getSearcherManager().getCachedData(getCatalogId(), "module", inModuleId);
		return module;
	}

	protected boolean isEditorFor(Data inData)
	{
		if(inData == null) {
			return false;
		}
		Collection users = inData.getValues("editorusers");
		if (users != null && !users.isEmpty() )
		{
			if( users.contains(getUserProfile().getUserId() ) )
			{	
				return true;
			}
		}
		Collection groups = inData.getValues("editorgroups");
		if (groups != null && !groups.isEmpty() )
		{
			Collection<Group> usergroups = getUserProfile().getUser().getGroups();
			for (Iterator iterator = usergroups.iterator(); iterator.hasNext();)
			{
				Group group = (Group) iterator.next();
				if( groups.contains(group.getId()) )
				{
					return true;
				}
			}
		}
		Collection roles = inData.getValues("editorroles");
		if (roles != null && !roles.isEmpty() )
		{
			if( roles.contains(getUserProfile().getId() ) )
			{	
				return true;
			}
		}
		return false;
	}
	
	protected boolean isViewerOnlySet(Data inEntity)
	{
		if(inEntity == null) {
			return false;
		}
		Collection users = inEntity.getValues("viewerusers");
		if (users != null && !users.isEmpty() )
		{
			if( users.contains(getUserProfile().getUserId() ) )
			{	
				return true;
			}
		}
		Collection groups = inEntity.getValues("viewergroups");
		if (groups != null && !groups.isEmpty() )
		{
			Collection<Group> usergroups = getUserProfile().getUser().getGroups();
			for (Iterator iterator = usergroups.iterator(); iterator.hasNext();)
			{
				Group group = (Group) iterator.next();
				if( groups.contains(group.getId()) )
				{
					return true;
				}
			}
		}
		Collection roles = inEntity.getValues("viewerroles");
		if (roles != null && !roles.isEmpty() )
		{
			if( roles.contains(getUserProfile().getId() ) )
			{	
				return true;
			}
		}
		return false;
	}
	
	protected boolean isEditorFor(Data inModule, Data inEntity)
	{
		boolean iseditor = isEditorFor(inModule) ||  isEditorFor(inEntity);
		return iseditor;
	}


	//System Level
	
	public Boolean can(String inKey)  //System wide settings
	{
		if(getSystemRolePermissions() != null) {
			boolean can = getSystemRolePermissions().contains(inKey);
			return can;
		}
		return false;
	}
	//Module Level
	public Boolean can(String inModuleId, String inKey)
	{
		return canModule(inModuleId,inKey);
	}
	public Boolean canModule(String inModuleId, String inKey)
	{
		Data module = loadModule(inModuleId);
		if( module == null )
		{
			log.error("No such module" + inModuleId);
			return false;
		}
		boolean can = canModule(module,inKey);
		return can;
	}
	public Boolean canModule(Data inModule, String inKey)
	{
		if(inModule == null) {
			return false;
		}
		
		if( inKey.equals("edit") )
		{
			boolean istrue = isEditorFor(inModule);
			if( istrue )
			{
				return true;
			}
		}
		Collection<Group> groups = getUserProfile().getGroups();
		for (Iterator iterator = groups.iterator(); iterator.hasNext();)
		{
			Group group = (Group) iterator.next();
			Map<String,Boolean> entitypermissions = getModulePermissions(inModule.getId(), null,group.getId());
			Boolean can = entitypermissions.get(inKey);
			if( can != null )
			{
				return can;
			}	
			return false;
		}
		return false;
	}
	
	public Boolean canEntity(Data inModule, Data inEntity, String inKey)
	{
		if (inModule == null || inEntity == null || inKey == null)
		{
			return false;
		}
		
		if( inKey.equals("edit") )
		{
			/*
			if( isViewerOnlySet(inEntity) )
			{
				return false;
			}
			*/
			boolean istrue = isEditorFor(inModule,inEntity);
			if( istrue )
			{
				return true;
			}
		}
		Collection<Group> groups = getUserProfile().getGroups();
		HashSet groupids = new HashSet();
		String extragroup = findEntityPermissionLevel(inModule, inEntity);
		groupids.add(extragroup);

		for (Iterator iterator = groupids.iterator(); iterator.hasNext();)
		{
			String groupid = (String) iterator.next();
			groupids.add(groupid);
		}
		for (Iterator iterator = groups.iterator(); iterator.hasNext();)
		{
			Group group = (Group) iterator.next();
			String groupid = group.getId();
			Map<String,Boolean> entitypermissions = getModulePermissions(inModule.getId(), inEntity.getId(), groupid);
			Boolean can = entitypermissions.get(inKey);
			if( can != null )
			{
				return can;
			}	
		}
		boolean found = canModule(inModule, inKey);
		return found;

	}

	private String findEntityPermissionLevel(Data inModule, Data inEntity)
	{
		String groupid = getUserProfile().get("settingsgroup");
		
		boolean isowner = getUserProfile().getUserId().equals(inEntity.get("owner"));
		if( isowner )
		{
			return "owner";
		}	
		//Must be viewer either at entity or module level
		return groupid;
	}

	public Map<String,Boolean> getModulePermissions(String inModuleId,String entityId, String inGroup)
	{
		String id = inModuleId + "_" + entityId + "_" + inGroup;
		Map<String,Boolean> modulepermissions = (Map<String,Boolean>)getSearcherManager().getCacheManager().get("permissions" + getCatalogId(),id);
		if( modulepermissions == null)
		{
			Searcher searcher = getSearcherManager().getSearcher(getCatalogId(), "permissionentityassigned");
			QueryBuilder query = searcher.query().exact("group",inGroup);
			if( entityId != null)
			{
				query.exact("entityid", entityId);
			}
			else
			{
				query.exact("moduleid", inModuleId);
			}
			HitTracker grouppermissions = query.search();
			modulepermissions = new HashMap<String,Boolean>();
			for (Iterator iterator = grouppermissions.iterator(); iterator.hasNext();)
			{
				Data data = (Data) iterator.next();
				String permissionname = data.get("permissionsentity");
				Object val = data.getValue("enabled");
				//modulepermissions.putPermission(permissionname, val);
				modulepermissions.put(permissionname, Boolean.valueOf(val.toString()));
			}
			getSearcherManager().getCacheManager().put("permissions" + getCatalogId(),id, modulepermissions);
		}
		
		return modulepermissions;

	}

	
}
