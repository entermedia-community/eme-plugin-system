/*
 * Created on Jan 28, 2005
 */
package org.openedit.page.manage;

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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.openedit.Generator;
import org.openedit.OpenEditException;
import org.openedit.config.Configuration;
import org.openedit.config.Script;
import org.openedit.config.Style;
import org.openedit.generators.CompositeGenerator;
import org.openedit.generators.GeneratorWithAcceptFilter;
import org.openedit.generators.GeneratorWithMimeTypeFilter;
import org.openedit.page.Page;
import org.openedit.page.PageAction;
import org.openedit.page.PageProperty;
import org.openedit.page.PageSettings;
import org.openedit.page.Permission;
import org.openedit.page.XconfConfiguration;
import org.openedit.repository.RepositoryException;
import org.openedit.util.OutputFiller;
import org.openedit.util.PathUtilities;
import org.openedit.util.XmlUtil;
import org.openedit.util.strainer.Filter;
import org.openedit.util.strainer.FilterReader;

/**
 * @author cburkey
 *
 */
public class XConfToPageSettingsConverter
{
	private static final Log log = LogFactory.getLog(XConfToPageSettingsConverter.class);
	protected PageSettingsManager fieldPageSettingsManager;
	protected FilterReader fieldFilterReader;
	protected XmlUtil fieldXmlUtil = new XmlUtil();
	protected OutputFiller fieldOutputFiller = new OutputFiller();

	protected List loadActions(PageSettings inSettings, List inPageActionList) throws OpenEditException
	{
		if (inPageActionList.size() == 0)
		{
			return null;
		}
		List pageActions = new ArrayList(inPageActionList.size());
		Iterator pageActionElements = inPageActionList.iterator();
		while (pageActionElements.hasNext())
		{
			Configuration pageActionElement = (Configuration) pageActionElements.next();
			PageAction currentPageAction = createAction(inSettings, pageActionElement);
			pageActions.add(currentPageAction);
		}
		return pageActions;
	}

	protected List loadScripts(PageSettings inSettings, List inScripts) throws OpenEditException
	{
		if (inScripts.size() == 0)
		{
			return null;
		}
		List pageActions = new ArrayList(inScripts.size());
		Iterator pageActionElements = inScripts.iterator();
		while (pageActionElements.hasNext())
		{
			Configuration pageActionElement = (Configuration) pageActionElements.next();
			Script script = createScript(inSettings, pageActionElement);
			pageActions.add(script);
		}
		return pageActions;
	}

	protected List loadStyles(PageSettings inSettings, List inStyles) throws OpenEditException
	{
		if (inStyles.size() == 0)
		{
			return null;
		}
		List pageActions = new ArrayList(inStyles.size());
		Iterator pageActionElements = inStyles.iterator();
		while (pageActionElements.hasNext())
		{
			Configuration pageActionElement = (Configuration) pageActionElements.next();
			Style script = createStyle(inSettings, pageActionElement);
			pageActions.add(script);
		}
		return pageActions;
	}

	protected Script createScript(PageSettings inSettings, Configuration inConfigElement)
	{
		String cancel = inConfigElement.get("cancel");
		// if(cancel != null && cancel.equals("true") )
		// {
		// return null;
		// }
		Script script = new Script();
		script.setId(inConfigElement.get("id"));
		script.setSrc(inConfigElement.get("src"));
		script.setCancel(Boolean.parseBoolean(inConfigElement.get("cancel")));
		script.setDefer(Boolean.parseBoolean(inConfigElement.get("defer")));
		String external = inConfigElement.get("external");
		script.setExternal(Boolean.parseBoolean(external));
		script.setPath(inSettings.getPath());
		List extras = inConfigElement.getChildren("property");
		if (extras != null)
		{
			for (Iterator iterator = extras.iterator(); iterator.hasNext();)
			{
				Configuration child = (Configuration) iterator.next();
				String key = child.getAttribute("name");
				String val = child.getValue();
				if (key != null && val != null)
				{
					script.setProperty(key, val);
				}
			}
		}
		return script;
	}

	protected Style createStyle(PageSettings inSettings, Configuration inConfigElement)
	{
		Style style = new Style();
		style.setId(inConfigElement.get("id"));
		style.setHref(inConfigElement.get("href"));
		style.setExternal(Boolean.parseBoolean(inConfigElement.get("external")));
		style.setPreload(Boolean.parseBoolean(inConfigElement.get("preload")));
		style.setCancel(Boolean.parseBoolean(inConfigElement.get("cancel")));
		return style;
	}

	private PageAction createAction(PageSettings inSettings, Configuration inPageActionElement)
	{
		String actionName = inPageActionElement.getAttribute("name");
		PageAction currentPageAction = new PageAction(actionName);
		currentPageAction.setPath(inSettings.getXConf().getPath());
		currentPageAction.setConfig(inPageActionElement);
		currentPageAction.setIncludesAll(Boolean.parseBoolean(inPageActionElement.getAttribute("alltypes")));
		return currentPageAction;
	}

	// protected void loadAlternateContentFile(PageSettings inPageConfig, String inAlternatePath)
	// {
	// if (inAlternatePath != null)
	// {
	// String path = PathUtilities.resolveRelativePath(inAlternatePath, inPageConfig.getPath());
	// inPageConfig.setAlternateContentPath(path);
	// }
	// }

	protected void loadGenerators(PageSettings inPageConfig, Configuration inParentConfig) throws OpenEditException
	{
		if (inParentConfig == null)
		{
			return;
		}
		List allGens = new ArrayList(2);
		List root = inParentConfig.getChildren("generator"); // these are top level generators
		for (Iterator iter = root.iterator(); iter.hasNext();)
		{
			Configuration rootconfig = (Configuration) iter.next();
			Generator generator = createGenerator(rootconfig);
			allGens.add(generator);
		}
		inPageConfig.setGenerators(allGens);
	}

	protected Generator createGenerator(Configuration inRootconfig) throws OpenEditException
	{
		String name = inRootconfig.getAttribute("name");
		Generator generator = null;
		if (name.equals("composite"))
		{
			// now add any children to a list
			List children = inRootconfig.getChildren("generator");
			List all = new ArrayList(children.size());
			for (Iterator iter = children.iterator(); iter.hasNext();)
			{
				Configuration config = (Configuration) iter.next();
				Generator child = createGenerator(config);
				all.add(child);
			}
			CompositeGenerator composite = new CompositeGenerator();
			composite.setGenerators(all);
			generator = composite;
		}
		else
		{
			generator = getPageSettingsManager().getGenerator(name);
		}
		generator = addFilter(inRootconfig, generator);
		return generator;
	}

	protected Generator addFilter(Configuration config, Generator generator)
	{
		String types = config.getAttribute("mimetypes");
		if (types != null)
		{
			generator = new GeneratorWithMimeTypeFilter(generator, types);
		}
		String accepts = config.getAttribute("accepts");
		if (accepts != null)
		{
			generator = new GeneratorWithAcceptFilter(generator, accepts);
		}
		return generator;
	}

	protected void loadLayout(PageSettings inPageConfig, Configuration inLayoutConfig) throws OpenEditException
	{
		if (inLayoutConfig == null)
		{
			return;
		}
		String layoutPath = inLayoutConfig.getValue();
		if (layoutPath == null)
		{
			inPageConfig.setLayout(Page.BLANK_LAYOUT);
			return;
		}
		layoutPath = PathUtilities.resolveRelativePath(layoutPath, inPageConfig.getPath());
		if (layoutPath.equals(inPageConfig.getPath()))
		{
			// dont set layout to self
			inPageConfig.setLayout(null);
			return;
		}
		inPageConfig.setLayout(layoutPath);
	}

	protected void loadInnerLayout(PageSettings inPageConfig, Configuration inInnerLayoutConfig) throws OpenEditException
	{
		if (inInnerLayoutConfig == null)
		{
			return;
		}
		String innerLayoutPath = inInnerLayoutConfig.getValue();
		if (innerLayoutPath == null)
		{
			inPageConfig.setInnerLayout(Page.BLANK_LAYOUT);
			return;
		}
		innerLayoutPath = PathUtilities.resolveRelativePath(innerLayoutPath, inPageConfig.getPath());
		if (innerLayoutPath.equals(inPageConfig.getPath()))
		{
			// dont set layout to self
			inPageConfig.setInnerLayout(Page.BLANK_LAYOUT);
			return;
		}
		inPageConfig.setInnerLayout(innerLayoutPath);
	}

	protected void loadPermissionFilters(PageSettings inPageConfig, XconfConfiguration inConfig) throws OpenEditException
	{
		List permissions = new ArrayList();
		Filter viewf = getFilterReader().readFilterCollection(inConfig.getViewRequirements(), "view");
		if (viewf != null)
		{
			Permission per = new Permission();
			per.setName("view");
			per.setRootFilter(viewf);
			per.setPath(inPageConfig.getPath());
			permissions.add(per);
		}
		Filter edit = getFilterReader().readFilterCollection(inConfig.getEditRequirements(), "edit");
		if (edit != null)
		{
			Permission per = new Permission();
			per.setName("edit");
			per.setRootFilter(edit);
			per.setPath(inPageConfig.getPath());
			permissions.add(per);
		}
		for (Iterator iterator = inConfig.getChildIterator("permission"); iterator.hasNext();)
		{
			Configuration top = (Configuration) iterator.next();
			String name = top.getAttribute("name");
			Filter root = getFilterReader().readFilterCollection(top, name);
			Permission per = new Permission();
			per.setName(name);
			per.setRootFilter(root);
			per.setPath(inPageConfig.getPath());
			permissions.add(per);
		}
		if (permissions.size() > 0)
		{
			inPageConfig.setPermissions(permissions);
		}
	}

	protected Map loadProperties(List inPropertyList)
	{
		Map properties = new HashMap(inPropertyList.size());
		Iterator propertyElements = inPropertyList.iterator();
		while (propertyElements.hasNext())
		{
			Configuration propertyElement = (Configuration) propertyElements.next();
			String name = propertyElement.getAttribute("name");
			PageProperty property = new PageProperty(name);
			boolean hasvalue = false;
			for (Iterator iter = propertyElement.getChildIterator("value"); iter.hasNext();)
			{
				hasvalue = true;
				Configuration val = (Configuration) iter.next();
				String locale = val.getAttribute("locale");
				property.setValue(val.getValue(), locale); // TODO: Should I pass "" if its the default locale already?
			}
			if (!hasvalue)
			{
				String value = propertyElement.getValue();
				// if( value == null)
				// {
				// value = "";
				// }
				property.setValue(value, (Locale) null);
			}
			properties.put(name, property);
		}
		return properties;
	}

	public PageSettingsManager getPageSettingsManager()
	{
		return fieldPageSettingsManager;
	}

	public void setPageSettingsManager(PageSettingsManager inPageSettingManager)
	{
		fieldPageSettingsManager = inPageSettingManager;
	}

	/**
	 * @param inPageSetting
	 * @param inUrlPath
	 */
	public void configureXConf(PageSettings inPageSetting, String inUrlPath) throws OpenEditException
	{
		boolean fileexists = inPageSetting.exists();

		// xconf does not exist
		if (fileexists)
		{
			XconfConfiguration config = new XconfConfiguration();
			Element root = null;
			try
			{
				root = fieldXmlUtil.getXml(inPageSetting.getReader(), "UTF-8");
			}
			catch (Exception e)
			{
				log.error("Could not read: " + inUrlPath);
				throw new OpenEditException(e + "path: " + inPageSetting.getPath(), e, inUrlPath);
			}
			config.populate(root);
			inPageSetting.getProperties().putAll(loadProperties(config.getProperties()));
			loadPermissionFilters(inPageSetting, config);
			loadGenerators(inPageSetting, config);
			loadLayout(inPageSetting, config.getLayout());
			loadInnerLayout(inPageSetting, config.getInnerLayout());
			List pagea = loadActions(inPageSetting, config.getPageActions());
			inPageSetting.setPageActions(pagea);
			List patha = loadActions(inPageSetting, config.getPathActions());
			inPageSetting.setPathActions(patha);
			inPageSetting.setScripts(loadScripts(inPageSetting, config.getScripts()));
			inPageSetting.setStyles(loadStyles(inPageSetting, config.getStyles()));
			List loaders = loadPageLoaders(inPageSetting, config.getPageLoaders());
			inPageSetting.setPageLoaders(loaders);
		}

		loadFallBackDirectory(inPageSetting);
		if (!inUrlPath.endsWith("xconf"))
		{
			loadAlternativeContent(inPageSetting, inUrlPath);
		}

		// We never do this
		// String mime = inPageSetting.getPropertyValue("mimetype", null);
		// if (mime != null)
		// {
		// inPageSetting.setMimeType(mime);
		// }
	}

	protected List<PageLoaderConfig> loadPageLoaders(PageSettings inPageSetting, List inPageLoaders)
	{
		if (inPageLoaders.size() == 0)
		{
			return null;
		}
		List pageActions = new ArrayList(inPageLoaders.size());
		Iterator loaderElements = inPageLoaders.iterator();
		while (loaderElements.hasNext())
		{
			Configuration pageActionElement = (Configuration) loaderElements.next();
			PageLoaderConfig config = new PageLoaderConfig();
			config.setXmlConfig(pageActionElement);
			pageActions.add(config);
		}
		return pageActions;
	}

	protected void loadAlternativeContent(PageSettings inPageSetting, String inUrlPath) throws RepositoryException
	{
		// Look for some content to use. We always have at least our own xconf in here
		for (PageSettings fallbackDirectory : inPageSetting.getFallbackParents())
		{
			String alternativepath = fallbackDirectory.getPath();
			// alternativepath = PathUtilities.extractDirectoryPath(alternativepath);
			if (alternativepath.endsWith(".xconf"))
			{
				alternativepath = PathUtilities.extractDirectoryPath(alternativepath);
			}
			alternativepath += "/" + PathUtilities.extractFileName(inUrlPath);
			boolean fallbackcontentexists = getPageSettingsManager().getRepository().doesExist(alternativepath);
			if (fallbackcontentexists)
			{
				// log.info(original.hashCode());
				inPageSetting.setAlternateContentPath(alternativepath);
				return;
			}
			inPageSetting.setOriginalyExistedContentPath(false);
		}
		if (!inUrlPath.startsWith("/catalog") && inUrlPath.endsWith(".html"))
		{
			log.info("Content not found " + inUrlPath);
		}
	}

	/**
	 * @param inPageSetting
	 * @param inUrlPath
	 */
	protected void loadFallBackDirectory(PageSettings inPageSetting) throws OpenEditException
	{
		// if( inUrlPath.isEmpty() || inUrlPath.equals("/_site.xconf") )
		// {
		// //No fallback for top level
		// return;
		// }
		// loop over and find any other sub-fallbacks and add them to the chain
		List<PageSettings> fallBackParents = new ArrayList<PageSettings>(6);

		// Set this early so we resolve things as we go
		inPageSetting.setFallbackParents(fallBackParents);

		PageProperty nextfallBackDir = findFallbackDirectory(inPageSetting);
		addFallBackParents(inPageSetting, inPageSetting, nextfallBackDir, fallBackParents);
		sortByFolderName(fallBackParents);

		// Collections.sort(fallBackParents, new XConfToPageSettingsConverter.PageSettingsPathComparator());

		// for (PageSettings setting : fallBackParents)
		// {

		// String alternativepath = findAlternativePath(inPageSetting, setting, inUrlPath);
		// if (alternativepath != null)
		// {
		// PageSettings otherxconf = getPageSettingsManager().getPageSettings(alternativepath);
		// inPageSetting.setFallBack(otherxconf);
		// break;
		// }
		// }

		// if (fallBackParents.size() > 1)
		// {
		// PageSettings first = fallBackParents.get(1);
		// inPageSetting.setFallBack(first);
		// }

		// PageSettings first = fallBackParents.get(0);
		// inPageSetting.setFallBack(first);
	}

	public void sortByFolderName(List<PageSettings> fallBackParents)
	{
		if (fallBackParents.size() < 2)
		{
			return;
		}
		PageSettings first = fallBackParents.get(0);

		// Make ones that have no fallback directory go to the bottom of the list
		// Loop over the list and see who falls back to who. If one falls back to another then it should be
		// higher in the list. If one has no fallback directory then it should be at the bottom of the list.
		// Set<PageSettings> remainingFallbacks = new HashSet<PageSettings>(fallBackParents);
		// remainingFallbacks.remove(first);
		// while( remainingFallbacks.size() > 1)
		// {
		// PageSettings testing = remainingFallbacks.iterator().next();
		// for (PageSettings remaining: remainingFallbacks)
		// {
		// PageProperty fallBackDir = findFallbackDirectory(testing);
		// if (fallBackDir == null)
		// {
		// remainingFallbacks.remove(testing);
		// }
		// }

		// }

		// Collections.sort(fallBackParents, new PageSettingsPathComparator(first.getPath()));

		Collections.sort(fallBackParents, new FallBackRootsComparator(first));

		Collections.reverse(fallBackParents);

	}

	// public String findAlternativePath(PageSettings inCurrentFallback, PageSettings
	// inAlternativeFallback, PageSettings inCurrentPath)
	// {
	// try
	// {
	// String alt = findAlternativePath(inCurrentFallback, inAlternativeFallback,
	// inCurrentPath.getPath());
	// if (alt != null)
	// {
	// log.info("inCurrentPath: " + inCurrentPath.getPath() + " inCurrentFallback: " +
	// inCurrentFallback.getPath() + " inAlternativeFallback: " + inAlternativeFallback.getPath()
	// + " --> Alternative path: " + alt);
	// log.info("done");
	// }

	// return alt;
	// }
	// catch (Throwable e)
	// {
	// log.error("ERRRRRRROR: inCurrentPath: " + inCurrentPath.getPath() + " inCurrentFallback: " +
	// inCurrentFallback.getPath() + " inAlternativeFallback: " + inAlternativeFallback.getPath());
	// return null;
	// }
	// }

	// public String findAlternativePath(PageSettings inOriginal, PageSettings inPageSetting, String
	// inUrlPath)
	// {
	// // if (inUrlPath.indexOf(".") == -1)
	// // {
	// // log.info("inOriginal: " + inOriginal.getPath() + " inPageSetting: " + inPageSetting.getPath()
	// +
	// // " inUrlPath: " + inUrlPath);
	// // log.info("+++++++++" + inUrlPath);
	// // log.info("+++++++++");
	// // return null;
	// // }
	// String fallBackValue = null;
	// // this is a catch 22. If we don't have a 1st level fallback set it might not look for second
	// level
	// PageProperty fallBackDir = inPageSetting.getProperty("fallbackdirectory");
	// String alternativepath = null;
	// if (fallBackDir != null && fallBackDir.getValue() != null)
	// {
	// String fallbacksetpath = fallBackDir.getPath();
	// fallBackValue = fallBackDir.getValue();
	// // 1. First is looks in mattcatalog. But there we want to use another fallback
	// // this might be using a variable. The value for this comes from the parent
	// fallBackValue = inOriginal.replaceProperty(fallBackValue);
	// fallBackValue = inOriginal.getParent().replaceProperty(fallBackValue);
	// if (fallBackValue.equals("/"))
	// {
	// fallBackValue = "";
	// }
	// if (fallBackValue.endsWith("/"))
	// {
	// throw new OpenEditException("Fall back setting must not end in slash for " + inUrlPath);
	// }
	// if (fallBackValue.equals("NO_FALLBACK"))
	// {
	// return null;
	// }
	// // Lets support relative paths ../A -> ../B
	// if (fallBackValue.contains(".."))
	// {
	// // Need to make sure we add back in the extra stuff
	// String fbthisdir = PathUtilities.extractDirectoryPath(fallbacksetpath); // what level the path
	// was defined
	// String newfallBackValue = PathUtilities.buildRelative(fallBackValue, fbthisdir);
	// // Need to add on any extra subdirectories or file parts
	// String filepart = inUrlPath.substring(fbthisdir.length(), inUrlPath.length()); // Just want the
	// end part
	// if (!filepart.endsWith(".xconf"))
	// {
	// filepart = PathUtilities.extractPagePath(filepart) + ".xconf"; // Take off the index.html... Use
	// index.xconf?
	// }
	// alternativepath = newfallBackValue + filepart; // end part might be a file name or _site.xconf
	// }
	// else
	// {
	// String thisdir = PathUtilities.extractDirectoryPath(fallbacksetpath); // what level the path was
	// defined
	// String filepart = inUrlPath.substring(thisdir.length(), inUrlPath.length());
	// alternativepath = fallBackValue + filepart; // end part might be a file name or _site.xconf
	// if (alternativepath.equals(inUrlPath))
	// {
	// // Now sure why this happens
	// // log.debug(inUrlPath + " Cannot specify self as fallback directory");
	// return null;
	// }
	// }
	// // Only default the site.xconf May get infinite loops
	// // if( inUrlPath.equals("/_site.xconf") || inUrlPath.startsWith("/system/") ||
	// // inUrlPath.startsWith("/openedit/") )
	// // if (inUrlPath.startsWith("/WEB-INF/base"))
	// // {
	// // // No fallback found.
	// // return null;
	// // }
	// // else
	// // {
	// // alternativepath = "/WEB-INF/base" + inUrlPath;
	// // }
	// }
	// return alternativepath;
	// }

	/**
	 * These are xconfs that are found in various places in the fallback chain. They are used to find
	 * content and properties to use if the current path does not have them. They are not used for
	 * layout, generators, or permissions. They are only used for content and properties. They are also
	 * used to find alternative content if the current path does not have any.
	 * 
	 * @param inStartingPath
	 * @param inParent
	 * @param inFallBackParents
	 */
	protected void addFallBackParents(PageSettings inVariableRosolver, PageSettings inParentPath, PageProperty fallBackDir, Collection<PageSettings> inFallBackParents)
	{
		inFallBackParents.add(inParentPath);
		if (fallBackDir != null)
		{
			// Get the path the the same location as the parent but in the fallback directory. Then check to see
			// if that xconf has a fallback directory. If so, add it to the list and repeat.
			String nextpath = resolveFallbackPath(inVariableRosolver, inParentPath, fallBackDir);
			if (nextpath != null)
			{
				PageSettings nextxconf = getPageSettingsManager().getPageSettings(nextpath);
				PageProperty nextfallBackDir = findFallbackDirectory(nextxconf);
				addFallBackParents(inVariableRosolver, nextxconf, nextfallBackDir, inFallBackParents);
			}
		}
	}

	protected PageProperty findFallbackDirectory(PageSettings inNext)
	{
		while (inNext != null)
		{
			PageProperty fallBackDir = inNext.getFieldProperty("fallbackdirectory");
			if (fallBackDir != null && fallBackDir.getValue() != null)
			{
				return fallBackDir;
			}
			inNext = inNext.getParent();
		}
		return null;
	}

	protected String resolveFallbackPath(PageSettings inVariableRosolver, PageSettings inParentPath, PageProperty fallBackRootDir)
	{
		// e.g. /finder/find/components/_site.xconf -> /finder/find/

		String targetFallbackRoot = fallBackRootDir.getValue(); // /community/default
		targetFallbackRoot = inVariableRosolver.replaceProperty(targetFallbackRoot);

		if ("NO_FALLBACK".equals(targetFallbackRoot))
		{
			return null;
		}

		String fallBackDefinitionRoot = PathUtilities.extractDirectoryPath(fallBackRootDir.getPath()); // /finder/find/

		String endingPath = inParentPath.getPath().substring(fallBackDefinitionRoot.length(), inParentPath.getPath().length()); // /components/_site.xconf

		String finalPath = targetFallbackRoot + endingPath;
		return finalPath;
	}

	public FilterReader getFilterReader()
	{
		return fieldFilterReader;
	}

	public void setFilterReader(FilterReader inFilterReader)
	{
		fieldFilterReader = inFilterReader;
	}

	class FallBackRootsComparator implements java.util.Comparator<PageSettings>
	{
		PageSettings topPageSettings;

		public FallBackRootsComparator(PageSettings topPageSettings) {
			this.topPageSettings = topPageSettings;
		}

		// community/default/components vs finder/find/components/_site.xconf
		public int compare(PageSettings inFirst, PageSettings inSecond)
		{
			// See if one page settings has any fallbacks to the other one
			if (checkIsParent(inFirst, inSecond))
			{
				return 1;
			}
			if (checkIsParent(inSecond, inFirst))
			{
				return -1;
			}
			return 0;

		}

		// Is the second a child of the first? If so it should be lower in the list
		protected boolean checkIsParent(PageSettings inStartingPoint, PageSettings inUnown)
		{
			for (PageSettings fallback : inStartingPoint.getFallbackParents())
			{
				PageSettings folder = fallback;
				while (folder != null)
				{
					PageProperty fallbackdir = findFallbackDirectory(folder);
					if (fallbackdir != null)
					{
						String path = resolveFallbackPath(topPageSettings, folder, fallbackdir);
						if (path == null) // this folder has no
						{
							return false;
						}
						if (path.equals(inUnown.getPath()))
						{
							return true;
						}
						/// TODO: also Check all the parents paths of second
					}
					folder = folder.getParent();
				}
			}
			return false;
		}
		/*
		 * class PageSettingsPathComparator implements java.util.Comparator<PageSettings> { private String
		 * basePath; private String rootfolder;;
		 * 
		 * public PageSettingsPathComparator(String basePath) { this.basePath = basePath; rootfolder =
		 * PathUtilities.extractRootDirectory(basePath);
		 * 
		 * }
		 * 
		 * protected String makePath(String[] paths, int i) { StringBuffer sb = new StringBuffer(); for (int
		 * j = 0; j <= i; j++) { sb.append(paths[j]); if (j < i) { sb.append("/"); } } return sb.toString();
		 * }
		 * 
		 * public int compare(PageSettings inOne, PageSettings inTwo) { // weight the paths that are closer
		 * to the current path higher. So if the base path is /a/b/c/d and // we have fallbacks of /a/b/c,
		 * /a/b, and /a then we want to weight them in that order. We also want // to weight any paths with
		 * the word default higher than those without it. So if we have
		 * 
		 * // openedit/ sub1 -> finder -> default // openedit/ sub2 - > community
		 * 
		 * int weightOne = getWeight(inOne.getPath()); int weightTwo = getWeight(inTwo.getPath()); if
		 * (weightOne > weightTwo) { return 1; } if (weightOne < weightTwo) { return -1; } return 0; }
		 * 
		 * private int getWeight(String path) { if (path.equals(basePath)) { return 0; // same path should
		 * be lowest weight } int weight = 0; if (path.startsWith("/finder")) // TODO: Change this to look
		 * in plugins or some xml file for the weighting { weight = 1000; // push it down } if
		 * (path.startsWith("/community")) { weight = 2000; // push it down }
		 * 
		 * int countdefaults = path.split("default").length - 1; weight += countdefaults * 1000; // default
		 * gets a big boost
		 * 
		 * // int distance = getPathDistance(basePath, path); // weight += (100 - distance); // closer paths
		 * get a higher weight return weight; } }
		 */
	}
}
