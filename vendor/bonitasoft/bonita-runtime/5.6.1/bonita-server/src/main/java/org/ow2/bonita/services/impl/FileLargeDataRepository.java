/**
 * Copyright (C) 2010  BonitaSoft S.A.
 * BonitaSoft, 31 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA  02110-1301, USA.
 **/
package org.ow2.bonita.services.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ow2.bonita.services.LargeDataRepository;
import org.ow2.bonita.util.BonitaConstants;
import org.ow2.bonita.util.BonitaRuntimeException;
import org.ow2.bonita.util.ExceptionManager;
import org.ow2.bonita.util.Misc;

public class FileLargeDataRepository implements LargeDataRepository {

  private static final Logger LOG = Logger.getLogger(FileLargeDataRepository.class.getName());
  private final File base;
  private static final Object CONSTRUCTOR_MUTEX = new Object();
  private static final String INDEX_NAME = "index.txt";

  public void clean() {
    Misc.deleteDir(this.base, 10, 5);
    createRepository();
  }

  public boolean isEmpty() {
    return checkIsEmpty(this.base);
  }

  private boolean checkIsEmpty(File dir) {
    File[] files = dir.listFiles();
    if (files == null) {
      return true;
    }
    for (File file : files) {
      if (file.isFile() && !file.getName().equals(INDEX_NAME)) {
        return false;
      } else if (!checkIsEmpty(file)) {
        return false;
      }
    }
    return true;
  }

  public FileLargeDataRepository(final String value) {
    Misc.checkArgsNotNull(value);
    String property = "property:";
    String path = value;
    if (value.startsWith(property)) {
      path = System.getProperty(value.substring(property.length()));
    } else if (value.startsWith("${" + BonitaConstants.HOME + "}")) {
      path = path.replace("${" + BonitaConstants.HOME + "}", System.getProperty(BonitaConstants.HOME));
    }
    this.base = new File(path, "bonita-large-data-repository");
    if (LOG.isLoggable(Level.INFO)) {
      LOG.fine("Creating " + getClass().getName() + " with base: " + this.base);
    }
    createRepository();
  }

  public boolean deleteData(final List<String> categories, final String key) {
    try {
      File file = removeFromIndex(categories, key);
      if (file == null || !file.exists()) {
        return false;
      }
      if (file != null && file.exists()) {
        int counter = 0;
        while (!file.delete() && counter < 50) {
          counter++;
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            //nothing
          }
        }
      }
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    }
    return true;
  }

  public boolean deleteData(List<String> categories) {
    File dir = getPath(categories, false);
    if (dir != null && dir.exists()) {
      Misc.deleteDir(dir, 10, 5);
      if (!dir.exists()) {
        return true;
      }
    }
    return false;
  }

  public <T> T getData(final Class<T> clazz, final List<String> categories, final String key) {
    try {
      File file = getFromIndex(categories, key);

      if (file == null || !file.exists()) {
        return null;
      }
      return (T) getData(clazz, file);
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  public Set<String> getKeys(List<String> categories, String regex) {
    File dir = getPath(categories, false);
    if (dir == null || !dir.exists()) {
      return Collections.emptySet();
    }
    try {
      final File index = getIndex(categories);
      synchronized (index.getAbsolutePath()) {
        Properties properties = loadIndex(index);
        Enumeration<Object> keys = properties.keys();
        Set<String> result = new HashSet<String>();
        while (keys.hasMoreElements()) {
          String key = (String)keys.nextElement();
          if (regex == null || key.matches(regex)) {
            result.add(key);
          }
        }
        return result;
      }
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  public Set<String> getKeys(List<String> categories) {
    return getKeys(categories, null);
  }

  public <T> Map<String, T> getDataFromRegex(Class<T> clazz, List<String> categories, String regex) {
    File dir = getPath(categories, false);
    if (dir == null || !dir.exists()) {
      return Collections.emptyMap();
    }
    try {
      final File index = getIndex(categories);
      synchronized (index.getAbsolutePath()) {
        Properties properties = loadIndex(index);

        Map<String, T> result = new HashMap<String, T>();
        for (Entry<Object, Object> entry : properties.entrySet()) {
          final String key = (String)entry.getKey();
          final File f = new File(dir + File.separator + entry.getValue());
          if (f.isFile() && (regex == null || key.matches(regex))) {
            result.put(key, (T)getData(clazz, f));
          }
        }
        return result;
      }
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  public <T> Map<String, T> getData(final Class<T> clazz, final List<String> categories, final Collection<String> keys) {
    if (keys == null) {
      return Collections.emptyMap();
    }
    File dir = getPath(categories, false);
    if (dir == null || !dir.exists()) {
      return Collections.emptyMap();
    }
    try {
      final File index = getIndex(categories);
      synchronized (index.getAbsolutePath()) {
        Properties properties = loadIndex(index);

        Map<String, T> result = new HashMap<String, T>();
        for (Entry<Object, Object> entry : properties.entrySet()) {
          String key = (String)entry.getKey();
          if (keys.contains(key)) {
            final File f = new File(dir + File.separator + entry.getValue());
            if (f.isFile()) {
              result.put(key, (T)getData(clazz, f));

            } 
          }
        }
        return result;
      }
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  public <T> Map<String, T> getData(final Class<T> clazz, List<String> categories) {
    return getDataFromRegex(clazz, categories, null);
  }

  public void storeData(final List<String> categories, final String key, final Serializable value, final boolean overWrite) {
    try {
      File file = getFromIndex(categories, key);

      if (file != null && file.exists()) {
        if (!overWrite) {
          return;
        } else {
          file.delete();
        }
      }
      final int lastDotIndex = key.lastIndexOf('.');
      String extension = "";
      if (lastDotIndex > 0) {
        extension = key.substring(lastDotIndex);
      }
      final String fileName = UUID.randomUUID().toString() + extension;
      file = new File(getPath(categories, true) + File.separator + fileName);
      file.createNewFile();
      Misc.write(file, Misc.serialize(value));
      addEntryToIndex(categories, key, fileName);
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  public Set<String> getKeys() {
    Set<String> keys = new HashSet<String>();
    try {
      analysePath(this.base, keys);
    } catch (Exception e) {
      throw new BonitaRuntimeException(e);
    }
    return keys;
  }

  private void analysePath(File dir, Set<String> keys) throws FileNotFoundException, IOException {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && !file.getName().equals(INDEX_NAME)) {
          File index = new File(dir.getAbsolutePath() + File.separator + INDEX_NAME);
          synchronized (index.getAbsolutePath()) {
            Properties properties = loadIndex(index);
            for (Entry<Object, Object> entry : properties.entrySet()) {
              if (entry.getValue().equals(file.getName())) {
                keys.add((String)entry.getKey());        
              }
            }
          }
        } else if (file.isDirectory()) {
          analysePath(file, keys);
        }
      }
    }
  }

  public String getDataPath(List<String> categories, String key) {
		try {
			File file = getFromIndex(categories, key);
			return file.getPath();
		} catch (IOException e) {
			throw new BonitaRuntimeException(e);
		}
	}

  /* PRIVATE METHODS */

  @SuppressWarnings("unchecked")
  private <T> T getData(Class<T> clazz, File file) {
    try {
      byte[] value = Misc.getAllContentFrom(file);
      return (T) Misc.deserialize(value);
    } catch (IOException e) {
      throw new BonitaRuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new BonitaRuntimeException(e);
    }
  }

  private File getFromIndex(final List<String> categories, final String key) throws IOException {
    File index = getIndex(categories);
    synchronized (index.getAbsolutePath()) {
      Properties properties = loadIndex(index);
      String file = (String) properties.get(key);
      if (file == null) {
        return null;
      }
      return new File(getPath(categories, false) + File.separator + file);
    }
  }

  private File removeFromIndex(final List<String> categories, final String key) throws IOException {
    File index = getIndex(categories);
    synchronized (index.getAbsolutePath()) {
      Properties properties = loadIndex(index);
      String file = (String) properties.remove(key);
      storeIndex(index, properties);
      return new File(getPath(categories, false) + File.separator + file);
    }
  }

  private File getIndex(final List<String> categories) throws IOException {
    File dir = getPath(categories, true);
    File index = new File(dir.getAbsolutePath() + File.separator + INDEX_NAME);
    synchronized (dir.getAbsolutePath()) {
      if (!index.exists()) {
        index.createNewFile();
      } 
    }
    return index;
  }

  private void addEntryToIndex(final List<String> categories, final String key, final String value) throws FileNotFoundException, IOException {
    final File index = getIndex(categories);
    synchronized (index.getAbsolutePath()) {
      Properties properties = loadIndex(index);
      properties.setProperty(key, value);
      storeIndex(index, properties);
    }
  }

  private Properties loadIndex(final File index) throws FileNotFoundException, IOException {
    Properties properties = new Properties();
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(index);
      properties.load(fis);
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
    return properties;
  }

  private void storeIndex(final File index, final Properties properties) throws FileNotFoundException, IOException {
    FileOutputStream fos = new FileOutputStream(index);
    properties.store(fos, null);
    fos.close();
  }

  public File getPath(final List<String> categories, final boolean create) {
    List<String> convertedCategories = convertCategories(categories);

    File dir = base;
    if (categories != null) {
      for (String category : convertedCategories) {
        File file = getFile(dir, category);
        if (file == null && create) {
          dir = new File(dir.getAbsolutePath() + File.separator + category);
          dir.mkdirs();
        } else if (file == null) {
          return null;
        } else {
          dir = file;
        }
      }
    }
    return dir;
  }

  private List<String> convertCategories(final List<String> categories) {
    List<String> result = new ArrayList<String>();
    for (String category : categories) {
      result.add(Misc.convertToJavaIdentifier(category));
    }
    return result;
  }

  private File getFile(final File currentDir, final String category) {
    File[] files = currentDir.listFiles();
    if (files == null || files.length == 0) {
      return null;
    }
    for (File file : files) {
      if (file.isDirectory() && file.getName().equals(category)) {
        return file;
      }
    }
    return null;
  }

  private void createRepository() {
    if (LOG.isLoggable(Level.CONFIG)) {
      LOG.config("Configuring Large Data Repository: " + FileLargeDataRepository.class.getName() + " with base: " + base);
    }
    // if repo directory does not exist, create it in mutual exclusion
    // all is done in synchronized blocks, because double-checked locking pattern is broken
    synchronized (CONSTRUCTOR_MUTEX) {
      if (this.base.exists()) {
        if (!this.base.isDirectory() || !this.base.canRead() || !this.base.canWrite()) {
          String message = ExceptionManager.getInstance().getFullMessage("bai_QDAPII_20", this.base.getAbsolutePath());
          throw new BonitaRuntimeException(message);
        }
      } else if (!this.base.mkdirs()) {
        String message = ExceptionManager.getInstance().getFullMessage("bai_QDAPII_21", this.base.getAbsolutePath());
        throw new BonitaRuntimeException(message);
      }
    }
  }

}
