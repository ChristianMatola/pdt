/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zend and IBM - Initial implementation
 *******************************************************************************/
package org.eclipse.php.internal.debug.core.pathmapper;

import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.util.preferences.IXMLPreferencesStorable;
import org.eclipse.php.internal.core.util.preferences.XMLPreferencesReader;
import org.eclipse.php.internal.core.util.preferences.XMLPreferencesWriter;
import org.eclipse.php.internal.debug.core.PHPDebugPlugin;
import org.eclipse.php.internal.debug.core.preferences.*;
import org.eclipse.php.internal.server.core.Server;
import org.eclipse.php.internal.server.core.manager.IServersManagerListener;
import org.eclipse.php.internal.server.core.manager.ServerManagerEvent;
import org.eclipse.php.internal.server.core.manager.ServersManager;

public class PathMapperRegistry implements IXMLPreferencesStorable, IServersManagerListener, IPHPExesListener {

	private static final String PATH_MAPPER_PREF_KEY = PHPDebugPlugin.getID() + ".pathMapper"; //$NON-NLS-1$

	private static PathMapperRegistry instance;
	private HashMap<Server, PathMapper> serverPathMapper;
	private HashMap<PHPexeItem, PathMapper> phpExePathMapper;

	private PathMapperRegistry() {
		serverPathMapper = new HashMap<Server, PathMapper>();
		phpExePathMapper = new HashMap<PHPexeItem, PathMapper>();
		loadFromPreferences();
	}

	public synchronized static PathMapperRegistry getInstance() {
		if (instance == null) {
			instance = new PathMapperRegistry();
		}
		return instance;
	}

	/**
	 * Return path mapper which corresponding to the given PHPexe item
	 * @param phpExe PHPExe item
	 * @return path mapper, or <code>null</code> if there's no one
	 */
	public static PathMapper getByPHPExe(PHPexeItem phpExe) {
		PathMapper result = getInstance().phpExePathMapper.get(phpExe);
		if (result == null) {
			result = new PathMapper();
			getInstance().phpExePathMapper.put(phpExe, result);
			PHPexes.getInstance().addPHPExesListener(getInstance());
		}
		return result;
	}

	/**
	 * Return path mapper which corresponding to the given Server instance
	 * @param server Server instance
	 * @return path mapper, or <code>null</code> if there's no one
	 */
	public static PathMapper getByServer(Server server) {
		PathMapper result = getInstance().serverPathMapper.get(server);
		if (result == null) {
			result = new PathMapper();
			getInstance().serverPathMapper.put(server, result);
			//create the link to servers manager here in order not to create tightly coupled relationship
			ServersManager.addManagerListener(getInstance());
		}
		return result;
	}

	/**
	 * Returns path mapper associated with the given launch configuration
	 * @param launchConfiguration Launch configuration
	 * @return path mapper
	 */
	public static PathMapper getByLaunchConfiguration(ILaunchConfiguration launchConfiguration) {
		PathMapper pathMapper = null;
		try {
			String serverName = launchConfiguration.getAttribute(Server.NAME, (String) null);
			if (serverName != null) {
				pathMapper = getByServer(ServersManager.getServer(serverName));
			} else {
				String phpExe = launchConfiguration.getAttribute(PHPCoreConstants.ATTR_LOCATION, (String) null);
				if (phpExe != null) {
					pathMapper = getByPHPExe(PHPexes.getInstance().getItemForFile(phpExe));
				}
			}
		} catch (CoreException e) {
			PHPDebugPlugin.log(e);
		}
		return pathMapper;
	}

	@SuppressWarnings("unchecked")
	public void loadFromPreferences() {
		HashMap[] elements = XMLPreferencesReader.read(PHPProjectPreferences.getModelPreferences(), PATH_MAPPER_PREF_KEY);
		if (elements.length == 1) {
			restoreFromMap(elements[0]);
		}
	}

	public static void storeToPreferences() {
		XMLPreferencesWriter.write(PHPProjectPreferences.getModelPreferences(), PATH_MAPPER_PREF_KEY, getInstance());
	}

	@SuppressWarnings("unchecked")
	public synchronized void restoreFromMap(HashMap map) {
		serverPathMapper.clear();
		phpExePathMapper.clear();
		Iterator i = map.keySet().iterator();
		while (i.hasNext()) {
			HashMap entryMap = (HashMap) map.get(i.next());
			String serverName = (String) entryMap.get("server"); //$NON-NLS-1$
			String phpExeFile = (String) entryMap.get("phpExe"); //$NON-NLS-1$
			PathMapper pathMapper = new PathMapper();
			pathMapper.restoreFromMap((HashMap) entryMap.get("mapper")); //$NON-NLS-1$
			if (serverName != null) {
				Server server = ServersManager.getServer(serverName);
				if (server != null) {
					serverPathMapper.put(server, pathMapper);
				}
			} else if (phpExeFile != null) {
				PHPexeItem phpExeItem = PHPexes.getInstance().getItemForFile(phpExeFile);
				if (phpExeItem != null) {
					phpExePathMapper.put(phpExeItem, pathMapper);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public synchronized HashMap storeToMap() {
		HashMap elements = new HashMap();
		Iterator i = serverPathMapper.keySet().iterator();
		int c = 1;
		while (i.hasNext()) {
			HashMap entry = new HashMap();
			Server server = (Server) i.next();
			PathMapper pathMapper = serverPathMapper.get(server);
			entry.put("server", server.getName()); //$NON-NLS-1$
			entry.put("mapper", pathMapper.storeToMap()); //$NON-NLS-1$
			elements.put("pathMapper" + (c++), entry); //$NON-NLS-1$
		}
		i = phpExePathMapper.keySet().iterator();
		while (i.hasNext()) {
			HashMap entry = new HashMap();
			PHPexeItem phpExeItem = (PHPexeItem) i.next();
			PathMapper pathMapper = phpExePathMapper.get(phpExeItem);
			entry.put("phpExe", phpExeItem.getPhpEXE().toString());
			entry.put("mapper", pathMapper.storeToMap());
			elements.put("pathMapper" + (c++), entry);
		}
		return elements;
	}

	public void serverAdded(ServerManagerEvent event) {
		if (!serverPathMapper.containsKey(event.getServer())) {
			serverPathMapper.put(event.getServer(), new PathMapper());
		}
	}

	public void serverModified(ServerManagerEvent event) {
	}

	public void serverRemoved(ServerManagerEvent event) {
		serverPathMapper.remove(event.getServer());
	}

	public void phpExeAdded(PHPExesEvent event) {
		if (!phpExePathMapper.containsKey(event.getPHPExeItem())) {
			phpExePathMapper.put(event.getPHPExeItem(), new PathMapper());
		}
	}

	public void phpExeRemoved(PHPExesEvent event) {
		phpExePathMapper.remove(event.getPHPExeItem());
	}
}
