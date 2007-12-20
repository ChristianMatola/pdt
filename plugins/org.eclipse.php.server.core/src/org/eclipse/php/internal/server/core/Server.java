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
package org.eclipse.php.internal.server.core;

import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.project.options.PHPProjectOptions;
import org.eclipse.php.internal.core.util.preferences.IXMLPreferencesStorable;

/**
 * A generic server implementation.
 */
public class Server implements IXMLPreferencesStorable {

	// Used as a root element name when saving and loading the preferences.
	public static final String SERVER_ELEMENT = "server";

	// Server properties.
	public static final String NAME = "name";
	public static final String BASE_URL = "base_url";
	public static final String DOCUMENT_ROOT = "document_root";
	public static final String PORT = "port";
	public static final String HOSTNAME = "hostname";
	public static final String FILE_NAME = "file_name";

	private static final int DEFAULT_HTTP_PORT = 80;

	private ServerHelper helper;

	/**
	 * Constructs a new Server
	 */
	public Server() {
		helper = new ServerHelper(this);
	}

	/**
	 * Constructs a new Server.
	 *
	 * @param name
	 * @param hostName
	 * @param baseURL
	 * @param documentRoot
	 * @param publish
	 */
	public Server(String name, String host, String baseURL, String documentRoot) {
		this();
		setName(name);
		setHost(host);
		setBaseURL(baseURL);
		setDocumentRoot(documentRoot);
	}

	/**
	 * Add a property change listener to this server.
	 *
	 * @param listener java.beans.PropertyChangeListener
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		helper.addPropertyChangeListener(listener);
	}

	/**
	 * Remove a property change listener from this server.
	 *
	 * @param listener java.beans.PropertyChangeListener
	 */
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		helper.removePropertyChangeListener(listener);
	}

	/**
	 * Sets an arbitrary attribute to this Server.
	 *
	 * @param attributeName The attribute name
	 * @param value The String value of this attribute.
	 */
	public void setAttribute(String attributeName, String value) {
		helper.setAttribute(attributeName, value);
	}

	/**
	 * Returns an arbitrary attribute from this Server according to a given attribute name.
	 *
	 * @param attributeName The attribute name
	 * @param defaultValue A default value to use if the attribute was not found
	 * @return The String value of this attribute
	 */
	public String getAttribute(String attributeName, String defaultValue) {
		return helper.getAttribute(attributeName, defaultValue);
	}

	/**
	 * Removed an attribute.
	 *
	 * @param attributeName The attribute name.
	 */
	public void removeAttribute(String attributeName) {
		helper.removeAttribute(attributeName);
	}

	public String getContextRoot(IProject project) {
		PHPProjectOptions options = PHPProjectOptions.forProject(project);
		String contextRoot = (String) options.getOption(PHPCoreConstants.PHPOPTION_CONTEXT_ROOT);

		if (contextRoot == null || contextRoot.equals("")) {
			contextRoot = project.getFullPath().toString();
		}

		return contextRoot;
	}

	public String getName() {
		return getAttribute(Server.NAME, "");
	}

	public void setName(String name) {
		setAttribute(Server.NAME, name);
	}

	public String getBaseURL() {
		return getAttribute(Server.BASE_URL, "");
	}

	public void setBaseURL(String url) {
		setAttribute(Server.BASE_URL, url);
	}

	public String getHost() {
		return getAttribute(Server.HOSTNAME, "localhost");
	}

	public void setHost(String host) {
		setAttribute(Server.HOSTNAME, host);
	}

	public void setDocumentRoot(String docRoot) {
		setAttribute(Server.DOCUMENT_ROOT, docRoot);
	}

	public String getDocumentRoot() {
		return getAttribute(Server.DOCUMENT_ROOT, "");
	}

	/**
	 * Return the root URL of this server.
	 *
	 * @return java.net.URL
	 */
	public URL getRootURL() {

		try {
			String port = getPortString();
			String base = getBaseURL();
			if (base.equals(""))
				base = "http://" + getHost();

			URL url = null;

			if (port.equals("80"))
				url = new URL(base + "/");
			else
				url = new URL(base + ":" + port + "/");

			return url;

		} catch (Exception e) {
			Logger.logException("Could not get root URL", e);
			return null;
		}

	}

	protected static String renderCommandLine(String[] commandLine, String separator) {
		if (commandLine == null || commandLine.length < 1)
			return "";
		StringBuffer buf = new StringBuffer(commandLine[0]);
		for (int i = 1; i < commandLine.length; i++) {
			buf.append(separator);
			buf.append(commandLine[i]);
		}
		return buf.toString();
	}

	public int getPort() {
		int port = Integer.parseInt(getPortString());
		if (port < 0) {
			port = DEFAULT_HTTP_PORT;
		}
		return port;
	}

	public String getPortString() {
		return getAttribute(Server.PORT, "80");
	}

	public void setPort(String port) {
		try {
			if (port.equals("")) {
				setAttribute(Server.PORT, "80");
			} else {
				setAttribute(Server.PORT, port);
			}
		} catch (Throwable e) {
			;
		}
	}

	public int hashCode() {
		if (getName() != null) {
			return getName().hashCode();
		}
		return 1;
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Server)) {
			return false;
		}
		String name = getName();
		String otherName = ((Server) obj).getName();
		if (name == null) {
			if (otherName != null) {
				return false;
			}
		}
		return name.equals(otherName);
	}

	/**
	 * Return a string representation of this Server.
	 * @return java.lang.String
	 */
	public String toString() {
		return "Server [" + getName() + "::"+ getHost() + ']';
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.php.internal.core.util.preferences.IXMLPreferencesStorable#restoreFromMap(java.util.HashMap)
	 */
	public void restoreFromMap(HashMap map) {
		HashMap properties = (HashMap) map.get(SERVER_ELEMENT);
		// This will cause for property change events to be fired on every attribute set.
		Iterator keys = properties.keySet().iterator();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			setAttribute(key, (String) properties.get(key));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.php.internal.core.util.preferences.IXMLPreferencesStorable#storeToMap()
	 */
	public HashMap storeToMap() {
		HashMap properties = new HashMap(helper.map);
		HashMap serverMap = new HashMap(1);
		serverMap.put(SERVER_ELEMENT, properties);
		return serverMap;
	}

	/**
	 * Checks whether this server is local machine
	 * @return
	 */
	public boolean isLocal() {
		try {
			String host = getHost();
			if (host != null) {
				InetAddress addr = InetAddress.getByName(host);
				if (addr != null) {
					NetworkInterface intf = NetworkInterface.getByInetAddress(addr);
					return intf != null;
				}
			}
		} catch (Exception e) {
		}
		return false;
	}
}
