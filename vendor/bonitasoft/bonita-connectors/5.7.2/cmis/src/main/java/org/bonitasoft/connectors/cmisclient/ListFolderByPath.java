/**
 * Copyright (C) 2010 BonitaSoft S.A.
 * BonitaSoft, 31 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bonitasoft.connectors.cmisclient;

import java.util.List;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.bonitasoft.connectors.cmisclient.common.AbstractCmisConnector;
import org.ow2.bonita.connector.core.ConnectorError;

/**
 * 
 * @author Frederic Bouquet
 * 
 */
public class ListFolderByPath extends AbstractCmisConnector {

	private String folderPath;
	private Iterable<CmisObject> folderContent;

	@Override
	protected void executeConnector() throws Exception {
		folderContent = listFolderByPath(username, password, url, binding_type,
				repositoryName, folderPath);
	}

	@Override
	protected List<ConnectorError> validateValues() {
		return null;
	}

	public void setFolderPath(final String folderPath) {
		this.folderPath = folderPath;
	}

	public Iterable<CmisObject> getFolderContent() {
		return folderContent;
	}

	protected Iterable<CmisObject> listFolderByPath(final String username,
			final String password, final String url, final String binding,
			final String repositoryName, final String path) {
		final Folder folder = getFolderByPath(username, password, url, binding,
				repositoryName, path);
		return folder.getChildren();
	}

}