/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.ui.navigator;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.buildship.core.CorePlugin;

/**
 * Allows users to show or hide the sub projects in the Navigator, Project and Package Explorer.
 *
 * @author Stefan Oehme
 */
public final class SubProjectViewerFilter extends ViewerFilter {

    @SuppressWarnings({"cast", "RedundantCast"})
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
        IResource resource = (IResource) Platform.getAdapterManager().getAdapter(element, IResource.class);
        return resource == null || !isBuildFolder(resource);
    }

    private boolean isBuildFolder(IResource resource) {
        return resource instanceof IFolder && CorePlugin.workspaceOperations().isSubProject((IFolder) resource);
    }

}
