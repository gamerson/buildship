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
package org.eclipse.buildship.core.workspace.internal;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import org.eclipse.buildship.core.GradlePluginsRuntimeException;

/**
 * Updates the derived resource markers of a project. Stores the last state in the preferences, so
 * we can remove the derived markers later.
 *
 * @author Stefan Oehme
 */
public final class DerivedResourcesUpdater {

    private DerivedResourcesUpdater() {
    }

    public static void updateDerivedResources(IProject project, List<IFolder> derivedResources, IProgressMonitor monitor) {
        SubMonitor progress = SubMonitor.convert(monitor, 2);
        try {
            clearDerivedResources(project, progress.newChild(1));
            SubMonitor updateProgress = progress.newChild(1).setWorkRemaining(derivedResources.size());
            StringSetProjectProperty derivedResourceProperty = getDerivedResourceProperty(project);
            for (IResource derivedResource : derivedResources) {
                if (!project.equals(derivedResource.getProject())) {
                    throw new IllegalArgumentException(String.format("Resource %s does not belong to project %s", derivedResource.getFullPath(), project.getFullPath()));
                }
                if (derivedResource.exists()) {
                    derivedResource.setDerived(true, updateProgress.newChild(1));
                } else {
                    updateProgress.worked(1);
                }
                derivedResourceProperty.add(derivedResource.getProjectRelativePath().toString());
            }
        } catch (CoreException e) {
            throw new GradlePluginsRuntimeException(e);
        } finally {
            monitor.done();
        }
    }

    public static void clearDerivedResources(IProject project, IProgressMonitor monitor) {
        StringSetProjectProperty derivedResourceProperty = getDerivedResourceProperty(project);
        Set<String> knownDerivedResources = derivedResourceProperty.get();
        SubMonitor progress = SubMonitor.convert(monitor, knownDerivedResources.size());
        try {
            for (String derivedResource : knownDerivedResources) {
                IFolder folder = project.getFolder(derivedResource);
                if (folder.exists()) {
                    folder.setDerived(false, progress.newChild(1));
                } else {
                    progress.worked(1);
                }
                derivedResourceProperty.remove(derivedResource);
            }
        } catch (CoreException e) {
            throw new GradlePluginsRuntimeException(e);
        } finally {
            monitor.done();
        }
    }

    private static StringSetProjectProperty getDerivedResourceProperty(IProject project) {
        return StringSetProjectProperty.from(project, "derived.resources");
    }
}
