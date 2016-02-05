/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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