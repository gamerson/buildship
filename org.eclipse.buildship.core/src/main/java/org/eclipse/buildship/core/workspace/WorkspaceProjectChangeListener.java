/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.workspace;

import java.util.Set;

import com.google.common.collect.Sets;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;

/**
 * Base class for listening to {@link IProject} addition and removal.
 *
 * @author Stefan Oehme
 */
public abstract class WorkspaceProjectChangeListener implements IResourceChangeListener {

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        // resource creation/deletion events are bundled in the POST_CHANGE type
        if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
            Set<IProject> addedProjects = Sets.newHashSet();
            Set<IProject> deletedProjects = Sets.newHashSet();
            collectProjects(event.getDelta(), addedProjects, deletedProjects);
            if (!addedProjects.isEmpty()) {
                notifyAboutProjectAdditions(addedProjects);
            }
            if (!deletedProjects.isEmpty()) {
                notifyAboutProjectRemovals(deletedProjects);
            }
        }
    }

    private void collectProjects(IResourceDelta delta, Set<IProject> addedProjects, Set<IProject> deletedProjects) {
        IResource resource = delta.getResource();
        if (resource instanceof IProject) {
            int kind = delta.getKind();
            if (kind == IResourceDelta.ADDED) {
                addedProjects.add((IProject) resource);
                return;
            } else if (kind == IResourceDelta.REMOVED) {
                deletedProjects.add((IProject) resource);
                return;
            }
        }

        // the resource delta object is hierarchical, thus we have to traverse its children to find
        // the project instances
        for (IResourceDelta child : delta.getAffectedChildren()) {
            collectProjects(child, addedProjects, deletedProjects);
        }
    }

    protected abstract void notifyAboutProjectAdditions(Set<IProject> addedProjects);

    protected abstract void notifyAboutProjectRemovals(Set<IProject> deletedProjects);

}