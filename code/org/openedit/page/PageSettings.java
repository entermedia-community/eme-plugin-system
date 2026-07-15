/*
 * Created on Jul 21, 2004
 */
package org.openedit.page;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.map.ListOrderedMap;
import org.openedit.Generator;
import org.openedit.OpenEditException;
import org.openedit.config.Configuration;
import org.openedit.config.Script;
import org.openedit.config.Style;
import org.openedit.data.ValuesMap;
import org.openedit.page.manage.PageLoaderConfig;
import org.openedit.page.manage.TextLabelManager;
import org.openedit.repository.ContentItem;
import org.openedit.util.PathUtilities;

/**
 * This class represents the possible meta data for a Page
 */
public class PageSettings
{
	private static final int MAX_DEPTH = 25; // prevent infinite loops when looking up fallbacks

	protected ContentItem fieldXConf;
	protected Configuration fieldUserDefinedData;
	protected long fieldModifiedTime;

	protected String fieldLayout;
	protected String fieldInnerLayout;
	protected List<Generator> fieldGenerators;
	protected ValuesMap fieldProperties;
	protected List fieldPageActions;
	protected List fieldScripts;
	protected List fieldStyles;
	protected List fieldPathActions;
	protected List fieldPageLoaders;
	protected TextLabelManager fieldTextLabels;
	protected String fieldAlternateContentPath;
	protected boolean fieldOriginalyExistedContentPath; // used to see if a new file has need added or removed

	protected List fieldPermissions;

	protected boolean fieldModified = false;
	protected String fieldMimeType;
	protected PageSettings fieldParent;
	protected String fieldDefaultLanguage;

	public String getAlternateContentPath()
	{
		return fieldAlternateContentPath;
	}

	public void setAlternateContentPath(String alternateContentPath)
	{
		fieldAlternateContentPath = alternateContentPath;
	}

	public String getPath()
	{
		return getXConf().getPath();
	}

	public String toString()
	{
		if (fieldXConf != null)
		{
			return getXConf().getPath();
		}
		return super.toString();
	}

	public List getGenerators()
	{
		List finalList = new ArrayList();
		int steps = 0;
		Set ids = new HashSet<>();
		while (steps < 20) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldGenerators != null)
				{
					for (Generator generator : chain.fieldGenerators)
					{
						// if (!ids.contains(generator.getId()))
						{
							ids.add(generator.getId());
							finalList.add(generator);
						}
					}
				}
			}
			steps++;
		}
		return finalList;
	}

	public void setGenerators(List generators)
	{
		fieldGenerators = generators;
	}

	public String getLayout()
	{
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				// log.info("Fallback parent: " + fallbackParent.getPath());
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null)
				{
					String layout = chain.getFieldLayout();
					if (layout != null)
					{
						String fixed = replaceProperty(layout);
						return fixed;
					}
				}
			}
			steps++;
		}
		return null;
	}

	// Not used
	public String getInnerLayout()
	{
		return getInnerLayoutExcludeSelf(null);
	}

	public String getInnerLayoutExcludeSelf(String inPath)
	{

		// Find the first inner layout that is closest to the current page
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				// log.info("Fallback parent: " + fallbackParent.getPath());
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null)
				{
					String innerlayout = chain.getFieldInnerLayout();
					if (innerlayout != null)
					{
						String fixed = replaceProperty(innerlayout);
						if (inPath == null || !inPath.equals(fixed))
						{
							return fixed;
						}
					}
				}
			}
			steps++;
		}
		return null;
	}

	protected PageSettings findParentAt(PageSettings inSettings, int inSteps)
	{
		PageSettings parent = inSettings;
		while (parent != null && inSteps > 0)
		{
			parent = parent.getParent();
			inSteps--;
		}
		return parent;
	}

	public void setLayout(String layout)
	{
		fieldLayout = layout;
	}

	public void setInnerLayout(String innerLayout)
	{
		fieldInnerLayout = innerLayout;
	}

	public String getParentFolder()
	{
		return PathUtilities.extractDirectoryName(getPath());
	}

	public String getParentPath()
	{
		return PathUtilities.extractDirectoryPath(getPath());
	}

	public List getPageActions()
	{
		List finalList = new ArrayList();
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldPageActions != null)
				{
					finalList.addAll(0, chain.fieldPageActions);
				}
			}
			steps++;
		}
		return finalList;
	}

	public List<Script> getScripts()
	{
		List finalList = new ArrayList();
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldScripts != null)
				{
					finalList.addAll(0, chain.fieldScripts);
				}
			}
			steps++;
		}
		return finalList;
	}

	public List<Style> getStyles()
	{
		List finalList = new ArrayList();
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldStyles != null)
				{
					finalList.addAll(0, chain.fieldStyles);
				}
			}
			steps++;
		}
		return finalList;
	}

	public void setPageActions(List pageActions)
	{
		fieldPageActions = pageActions;
	}

	public List getPathActions()
	{
		// add top level parents last
		List finalList = new ArrayList();
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldPathActions != null)
				{
					finalList.addAll(0, chain.fieldPathActions);
				}
			}
			steps++;
		}
		// Collections.reverse(finalList);
		return finalList;

	}

	protected int getDepth()
	{
		int i = 0;
		PageSettings settings = this;
		while (settings != null)
		{
			i++;
			settings = settings.getParent();
		}
		return i;
	}

	public ValuesMap getProperties()
	{
		if (fieldProperties == null)
		{
			fieldProperties = new ValuesMap();
		}
		return fieldProperties;
	}

	public Map getMap()
	{
		ValuesMap newmap = new ValuesMap();
		for (Iterator iterator = getProperties().keySet().iterator(); iterator.hasNext();)
		{
			Object key = (Object) iterator.next();
			Object value = getProperties().get(key);
			if (value != null)
			{
				newmap.put(key, value);
			}
		}
		return newmap;
	}

	public PageProperty getProperty(String inKey)
	{
		int steps = 0;
		Collection<PageSettings> knownfallbacks = getFallbackParents();
		if (knownfallbacks == null)
		{
			knownfallbacks = new ArrayList<>(1);
			knownfallbacks.add(this);
		}
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : knownfallbacks)
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null)
				{
					PageProperty val = chain.getFieldProperty(inKey);
					if (val != null)
					{
						return val;
					}
				}
			}
			steps++;
		}
		return null;
	}

	public PageProperty getFieldProperty(String inKey)
	{
		PageProperty val = (PageProperty) getMap().get(inKey);
		if (val != null)
		{
			val.setPath(getXConf().getPath());
		}
		return val;
	}

	public void setProperties(Map properties)
	{
		getProperties().putAll(properties);
	}

	public void putProperty(PageProperty inProperty)
	{
		getProperties().put(inProperty.getName(), inProperty);
	}

	protected Collection<PageSettings> fieldgetFallbackParents;

	// List all the fallbacks sorted by depth with the deepest first
	public Collection<PageSettings> getFallbackParents()
	{
		return fieldgetFallbackParents;
	}

	public void setFallbackParents(Collection<PageSettings> inFallbackParents)
	{
		fieldgetFallbackParents = inFallbackParents;
	}

	// Does this even work? Seems slow
	public boolean isCurrent()
	{
		// if (!chain.fieldIsCurrent())

		return true;
	}

	public boolean fieldIsCurrent()
	{
		// System.out.println("Checking " + getPath());
		long last = getXConf().getLastModified();
		// if ( last == -1)
		// {
		// return false;
		// }
		boolean self = last == getModifiedTime();
		return self;
	}

	public boolean exists()
	{
		return getXConf().exists();
	}

	public ContentItem getXConf()
	{
		return fieldXConf;
	}

	public void setXConf(ContentItem inConf)
	{
		fieldXConf = inConf;
		if (inConf != null)
		{
			setModifiedTime(inConf.getLastModified());
		}
		else
		{
			setModifiedTime(-1); // TODO: Why not have 0 in here?
		}
	}

	/**
	 * @return
	 */
	public Reader getReader() throws OpenEditException
	{
		try
		{
			// String enc = getPageCharacterEncoding(); //speed up
			// if (enc != null)
			// {
			// return new InputStreamReader(getXConf().getInputStream(), enc);
			// }
			return new InputStreamReader(getXConf().getInputStream(), "UTF-8");

		}
		catch (Exception ex)
		{
			throw new OpenEditException(ex);
		}
	}

	public long getModifiedTime()
	{
		return fieldModifiedTime;
	}

	public void setModifiedTime(long inLastModifiedTime)
	{
		fieldModifiedTime = inLastModifiedTime;
	}

	public String getPageCharacterEncoding()
	{
		return getPropertyValue("encoding", null);
	}

	public Configuration getUserDefinedData()
	{
		return fieldUserDefinedData;
	}

	public void setUserDefinedData(Configuration inUserDefinedData)
	{
		fieldUserDefinedData = inUserDefinedData;
	}

	/**
	 * @param inString
	 * @return
	 */
	public Configuration getUserDefined(String inString)
	{
		if (getUserDefinedData() != null)
		{
			Configuration config = getUserDefinedData().getChild(inString);
			if (config != null)
			{
				return config;
			}
		}
		return null;
	}

	/**
	 * @param inList
	 */
	public void setPathActions(List inList)
	{
		fieldPathActions = inList;
	}

	/**
	 * @param inString
	 * @return
	 */
	public String getPropertyValue(String inString, Locale inLocale)
	{
		PageProperty prop = getProperty(inString);
		if (prop != null)
		{
			return replaceProperty(prop.getValue(inLocale));
		}
		return null;

	}

	/**
	 * @return Returns the mimeType.
	 */
	public String getMimeType()
	{
		return fieldMimeType;
	}

	/**
	 * @param inMimeType The mimeType to set.
	 */
	public void setMimeType(String inMimeType)
	{
		fieldMimeType = inMimeType;
	}

	public PageSettings getParent()
	{
		return fieldParent;
	}

	public void setParent(PageSettings inParent)
	{
		fieldParent = inParent;
	}

	public String getFieldAlternativeContentPath()
	{
		return fieldAlternateContentPath;
	}

	public List getFieldGenerator()
	{
		return fieldGenerators;
	}

	public String getFieldInnerLayout()
	{
		return fieldInnerLayout;
	}

	public String getFieldLayout()
	{
		return fieldLayout;
	}

	public String getDefaultLanguage()
	{
		if (fieldDefaultLanguage == null)
		{
			fieldDefaultLanguage = getPropertyValue("defaultlanguage", null);
			if (fieldDefaultLanguage == null)
			{
				fieldDefaultLanguage = "";
			}
		}
		return fieldDefaultLanguage;
	}

	public String getPropertyValueFixed(String inKey)
	{
		String val = getPropertyValue(inKey, null);
		return replaceProperty(val);
	}

	public String replaceProperty(String inValue)
	{
		if (inValue == null)
		{
			return inValue;
		}
		String edittext = inValue;
		int start = 0;
		while ((start = edittext.indexOf("${", start)) != -1)
		{
			int end = edittext.indexOf("}", start);
			if (end != -1)
			{
				String key = edittext.substring(start + 2, end);
				Object variable = getProperty(key); // check for property

				if (variable != null)
				{
					String sub = variable.toString();
					sub = replaceProperty(sub);
					edittext = edittext.substring(0, start) + sub + edittext.substring(end + 1);
					if (sub.length() <= end)
					{
						start = end - sub.length();
					}
					else
					{
						start = sub.length();
					}
				}
				else
				{
					start = end;
				}
			}

		}
		return edittext;
	}

	public boolean isOriginalyExistedContentPath()
	{
		return fieldOriginalyExistedContentPath;
	}

	public void setOriginalyExistedContentPath(boolean inOriginalContentPathMissing)
	{
		fieldOriginalyExistedContentPath = inOriginalContentPathMissing;
	}

	public Permission getLocalPermission(String inName)
	{
		if (fieldPermissions == null)
		{
			return null;
		}
		return findPermission(fieldPermissions, inName);
	}

	public Permission getPermission(String inName)
	{
		int steps = 0;
		while (steps < MAX_DEPTH) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldPermissions != null)
				{
					Permission per = findPermission(chain.fieldPermissions, inName);
					if (per != null)
					{
						return per;
					}
				}
			}
			steps++;
		}
		return null;
	}

	protected Permission findPermission(List inList, String inName)
	{
		for (Iterator iterator = inList.iterator(); iterator.hasNext();)
		{
			Permission permission = (Permission) iterator.next();
			if (permission.getName().equals(inName))
			{
				return permission;
			}
		}
		return null;
	}

	/**
	 * Lets remove permissions at the xconf level Load up based on top level permissions being loaded
	 * first. If you override a top level one It will be loaded out of order but only once
	 * 
	 * @param includeself
	 * @return
	 */
	public List getPermissions()
	{
		Map finalList = ListOrderedMap.decorate(new HashMap());

		int steps = 0;
		while (steps < 10) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldPermissions != null)
				{
					for (int i = chain.fieldPermissions.size() - 1; i >= 0; i--)
					{
						Permission per = (Permission) chain.fieldPermissions.get(i);
						Permission old = (Permission) finalList.get(per.getName());

						if (old == null)
						{
							finalList.put(per.getName(), per);
						}
						else
						{
							// Move the old value up to this location
							finalList.remove(old.getName());
							finalList.put(old.getName(), old);
						}
					}
				}
			}
			steps++;
		}
		ArrayList all = new ArrayList(finalList.values());
		Collections.reverse(all);
		return all; // oldest on bottom
	}

	public List getFieldPermissions()
	{
		return fieldPermissions;
	}

	public void setPermissions(List inPermissions)
	{
		fieldPermissions = inPermissions;
	}

	public void addPermission(Permission inPermission)
	{
		removePermission(inPermission);
		if (fieldPermissions == null)
		{
			fieldPermissions = new ArrayList();
		}
		if (fieldPermissions.size() > 0)
		{
			fieldPermissions.add(0, inPermission);
		}
		else
		{
			fieldPermissions.add(inPermission);
		}

	}

	public void removePermission(Permission inPermission)
	{
		if (fieldPermissions != null)
		{

			for (Iterator iterator = fieldPermissions.iterator(); iterator.hasNext();)
			{
				Permission permission = (Permission) iterator.next();
				if (permission.getName().equals(inPermission.getName()))
				{
					fieldPermissions.remove(permission);
					break;
				}
			}
		}
	}

	public String getUserDefined(String inElement, String inAttribute)
	{
		Configuration pdata = getUserDefined(inElement);
		String val = null;
		if (pdata != null)
		{
			val = pdata.getAttribute(inAttribute);
		}
		return val;
	}

	public void removeProperty(String inName)
	{
		getProperties().remove(inName);
	}

	public void setProperty(String inName, String inValue)
	{
		if (inValue == null)
		{
			removeProperty(inName);
			return;
		}
		PageProperty prop = getFieldProperty(inName);
		if (prop == null)
		{
			prop = new PageProperty(inName);
			prop.setPath(getPath());
			getProperties().put(inName, prop);
		}
		prop.setValue(inValue);
	}

	public void addPathAction(PageAction inAction)
	{
		if (fieldPathActions == null)
		{
			fieldPathActions = new ArrayList();
		}
		fieldPathActions.add(inAction);
	}

	public void addPageAction(PageAction inAction)
	{
		if (fieldPageActions == null)
		{
			fieldPageActions = new ArrayList();
		}
		fieldPageActions.add(inAction);
	}

	public List getFieldPathActions()
	{
		return fieldPathActions;
	}

	public List getFieldPageActions()
	{
		return fieldPageActions;
	}

	public TextLabelManager getTextLabels()
	{
		return fieldTextLabels;
	}

	public void setTextLabels(TextLabelManager inTextLabels)
	{
		fieldTextLabels = inTextLabels;
	}

	public void setScripts(List inScripts)
	{
		fieldScripts = inScripts;

	}

	public void setStyles(List inStyles)
	{
		fieldStyles = inStyles;
	}

	public List<PageLoaderConfig> getPageLoaders()
	{
		// add top level parents last
		List finalList = new ArrayList();
		Set ids = new HashSet();

		int steps = 0;
		while (steps < 10) // prevent infinite loop
		{
			for (PageSettings fallbackParent : getFallbackParents())
			{
				PageSettings chain = findParentAt(fallbackParent, steps);
				if (chain != null && chain.fieldPageLoaders != null)
				{
					addLoaders(ids, finalList, chain.fieldPageLoaders);
				}
			}
			steps++;
		}
		return finalList;
	}

	private void addLoaders(Set hashset, List inFinalList, List<PageLoaderConfig> inPageLoaders)
	{
		if (inPageLoaders == null)
		{
			return;
		}
		for (int i = 0; i < inPageLoaders.size(); i++)
		{
			PageLoaderConfig config = inPageLoaders.get(i);
			if (!hashset.contains(config.getLoader()))
			{
				hashset.add(config.getLoader());
				inFinalList.add(config);
			}
		}

	}

	public void setPageLoaders(List<PageLoader> inLoaders)
	{
		fieldPageLoaders = inLoaders;
	}

}
