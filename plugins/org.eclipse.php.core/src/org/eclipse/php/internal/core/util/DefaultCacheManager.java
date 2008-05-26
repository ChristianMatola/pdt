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
package org.eclipse.php.internal.core.util;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.phpModel.parser.IPhpModel;
import org.eclipse.php.internal.core.phpModel.parser.PHPUserModel;
import org.eclipse.php.internal.core.phpModel.phpElementData.CodeData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFileData;
import org.eclipse.php.internal.core.preferences.PreferencesSupport;

/**
 * Created by Shachar Ben-Zeev.
 * @author shachar, shalom
 */
public class DefaultCacheManager {

	private static final String DATA_MODEL_FILE_NAME = ".dataModel";//$NON-NLS-1$
	private static final String CACHE_DIR_NAME = ".cache";//$NON-NLS-1$
	public static final int DEFAULT_CACHE_POLICY = 0;
	public static final int VERSION_IDENTIFIER = 181107; //DDMMYY
	private HashMap projectToCacheDir;
	private File sharedCacheDir;
	private PreferencesSupport preferencesSupport;
	private IncludeCacheManager includeCacheManager;
	private Object writeLock = new Object();

	public static DefaultCacheManager instance;

	/**
	 * Returns a DefaultCacheManager shared instance.
	 *
	 * @return
	 */
	public static DefaultCacheManager instance() {
		if (instance == null) {
			instance = new DefaultCacheManager();
		}
		return instance;
	}

	private DefaultCacheManager() {
		projectToCacheDir = new HashMap();
		IPath pluginLocation = PHPCorePlugin.getDefault().getStateLocation();
		sharedCacheDir = new File(pluginLocation.toFile(), CACHE_DIR_NAME);
		if (!sharedCacheDir.exists()) {
			sharedCacheDir.mkdirs();
		}
		preferencesSupport = new PreferencesSupport(PHPCorePlugin.ID, PHPCorePlugin.getDefault().getPreferenceStore());
		includeCacheManager = new IncludeCacheManager(this);
	}

	/**
	 * Returns an IncludeCacheManager.
	 *
	 * @return IncludeCacheManager
	 */
	public IncludeCacheManager getIncludeCacheManager() {
		return includeCacheManager;
	}

	/**
	 * Phisically delete a cache file according to it's key.
	 * This method is needed when the project is being run over with another
	 * project that has an identical name.
	 *
	 * @param key   The project name.
	 */
	public void deleteCacheFromDisk(IProject project) {
		File cacheDir = getCacheDir(project);
		File toDelete = new File(cacheDir, DATA_MODEL_FILE_NAME);
		if (toDelete.exists()) {
			toDelete.delete();
		}
	}

	/**
	 * Retunrs the cache directory (.caches) for the given project.
	 *
	 * @param project An IProject
	 * @return	The cache directory for the project.
	 */
	protected File getCacheDir(IProject project) {
		File file = (File) projectToCacheDir.get(project);
		if (file == null && project != null) {
			final IPath location = project.getLocation();
			if (location == null) {
				return null;
			}
			File path = location.toFile();
			if (path != null) {
				file = new File(path, CACHE_DIR_NAME);
				projectToCacheDir.put(project, file);
			}
		}
		if (!file.exists()) {
			file.mkdirs();
		}
		return file;
	}

	/**
	 * Returns the last modification time stamp for the cached file defined for the given project and model.
	 * Zero is returned if the cache file does not exists.
	 *
	 * @param project An IProject
	 * @param model An IPhpModel
	 * @return The last modification time stamp for the cached file.
	 */
	public long getSharedCacheModificationTime(IProject project, IPhpModel model) {
		File f = getSharedCacheFile(project, model);
		return f.lastModified();
	}

	/**
	 * Returns the shared cache directory.
	 *
	 * @return The shared directory used for caching include-paths and variables models.
	 */
	public File getSharedCacheDirectory() {
		return sharedCacheDir;
	}

	/**
	 * Returns the shared cache directory for the given project and model.
	 * The returned file name is composed from the model id (the library/zip path) hash, separated
	 * with a '_' mark and ends with the php version.
	 *
	 * @param project An IProject
	 * @param model	An IPhpModel (PHPUserModel is the only supported model)
	 * @return The shared cache directory for the given project and model.
	 */
	protected File getSharedCacheFile(IProject project, IPhpModel model) {
		return getSharedCacheFile(project, model.getID());
	}

	/**
	 * Returns the shared cache directory for the given project and model.
	 * The returned file name is composed from the library path hash, separated
	 * with a '_' mark and ends with the php version.
	 *
	 * @param project An IProject
	 * @param libraryPath	The library (directory / zip) path.
	 * @return The shared cache directory for the given project and model.
	 */
	public File getSharedCacheFile(IProject project, String libraryPath) {
		String phpVersion = preferencesSupport.getPreferencesValue(PHPCoreConstants.PHP_OPTIONS_PHP_VERSION, null, project);
		return getSharedCacheFile(phpVersion, libraryPath);
	}

	/**
	 * Returns the shared cache directory for the given php version and model.
	 * The returned file name is composed from the library path hash, separated
	 * with a '_' mark and ends with the php version.
	 *
	 * @param phpVersion A PHP version string
	 * @param libraryPath	The library (directory / zip) path.
	 * @return The shared cache directory for the given project and model.
	 */
	public File getSharedCacheFile(String phpVersion, String libraryPath) {
		String pathHash = String.valueOf(libraryPath.hashCode());
		String fileName = pathHash + '_' + (phpVersion != null ? phpVersion : "");//$NON-NLS-1$
		return new File(sharedCacheDir, fileName);
	}

	/**
	 * Loads a cached IPhpModel to the given model.
	 * If no such model can be resolved by the project and file, nothing will be added to the model.
	 * The only supported model for this class is PHPUserModel.
	 *
	 * @param project An IProject
	 * @param model	A IPhpModel (PHPUserModel)
	 * @param isShared Indicate if the model is shared with other projects.
	 * @return true if model was loaded successfully
	 */
	public boolean load(IProject project, IPhpModel model, boolean isShared) {
		if (project == null || !(model instanceof PHPUserModel)) {
			return false;
		}
		PHPUserModel userModel = (PHPUserModel) model;
		File cacheFile = null;
		if (isShared) {
			cacheFile = getSharedCacheFile(project, model);
		} else {
			cacheFile = new File(getCacheDir(project), DATA_MODEL_FILE_NAME);
		}
		return innerLoadModel(userModel, cacheFile);
	}

	/**
	 *  Load the model from the disk.
	 *
	 *  @return true if model was loaded successfully
	 */
	private boolean innerLoadModel(PHPUserModel userModel, File cacheFile) {
		if (!cacheFile.exists()) {
			return false;
		}
		boolean invalidCache = false;
//		Runtime.getRuntime().gc();
		FileInputStream in = null;
		BufferedInputStream bufin = null;
		DataInputStream din = null;
		try {
			in = new FileInputStream(cacheFile);
			bufin = new BufferedInputStream(in, 2048);
			din = new DataInputStream(bufin);

			int version = din.readInt();

			if (version == VERSION_IDENTIFIER) {
				PHPFileData[] datas = SerializationUtil.deserializePHPFileDataArray(din);
			
				if(datas.length > 0){
					Path path = new Path(datas[0].getName());
					String projectName = cacheFile.getParentFile().getParentFile().getName();
					if(!projectName.equals(path.segment(0))){
						return false;
					}
				}
				for (PHPFileData data : datas) {
					userModel.insert(data);
				}
				Runtime.getRuntime().gc();
			} else {
				invalidCache = true;
			}
		} catch (FileNotFoundException ex) {
		} catch (Exception e) {
			PHPCorePlugin.log(e);
			return false;
		} finally {
			StreamUtils.closeStream(din);
			StreamUtils.closeStream(bufin);
			StreamUtils.closeStream(in);
			if (invalidCache) {
				if (cacheFile.delete()) {
					String message = "Invalid cache version. The cache file was deleted.";//$NON-NLS-1$
					PHPCorePlugin.log(new Status(IStatus.INFO, PHPCorePlugin.ID, message, null));
				} else {
					String message = "Invalid cache version. Could not delete the file: " + cacheFile.getPath();//$NON-NLS-1$
					PHPCorePlugin.log(new Status(IStatus.INFO, PHPCorePlugin.ID, message, null));
				}
			}
		}
		return !invalidCache;
	}

	/**
	 * Saves a cache of a given IPhpModel.
	 * The only supported model for this class is PHPUserModel.
	 *
	 * @param project An IProject
	 * @param model	A IPhpModel (PHPUserModel)
	 * @param isShared Indicate if the model is shared with other projects.
	 */
	public void save(IProject project, IPhpModel model, boolean isShared) {
		if (!(model instanceof PHPUserModel)) {
			return;
		}
		PHPUserModel userModel = (PHPUserModel) model;
		File cacheFile = null;
		if (isShared) {
			cacheFile = getSharedCacheFile(project, model);
			synchronized (writeLock) {
				if (cacheFile.exists()) {
					// if the cached file is older then a minute, override it.
					if (System.currentTimeMillis() - cacheFile.lastModified() > 60000) {
						innerSaveModel(userModel, cacheFile);
					}
				} else {
					innerSaveModel(userModel, cacheFile);
				}
			}
		} else {
			cacheFile = new File(getCacheDir(project), DATA_MODEL_FILE_NAME);
			innerSaveModel(userModel, cacheFile);
		}
	}

	// Cache the model to the disk.
	private void innerSaveModel(PHPUserModel userModel, File cacheFile) {
		FileOutputStream out = null;
		BufferedOutputStream bufout = null;
		DataOutputStream dout = null;
		try {
			CodeData[] files = userModel.getFileDatas();
			ICachable[] toSave = Arrays.asList(files).toArray(new ICachable[files.length]);
			out = new FileOutputStream(cacheFile);
			bufout = new BufferedOutputStream(out, 2048);
			dout = new DataOutputStream(bufout);

			dout.writeInt(VERSION_IDENTIFIER);
			SerializationUtil.serialize(toSave, dout);
			dout.flush();
		} catch (IOException e) {
			PHPCorePlugin.log(e);
		} finally {
			StreamUtils.closeStream(dout);
			StreamUtils.closeStream(out);
			StreamUtils.closeStream(bufout);
		}
		Runtime.getRuntime().gc();
	}
}
