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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Finds the root projects that the given Eclipse projects belong to and synchronizes each of these
 * root projects with the Eclipse workspace.
 */
public class RefreshGradleProjectsJob extends SynchronizeGradleProjectsJob {

    private List<IProject> projects;

    public RefreshGradleProjectsJob(List<IProject> projects) {
        super("Synchronize workspace projects with Gradle counterparts", true);
        this.projects = projects;
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsToSynchronize() {
        return getUniqueRootProjects(this.projects);
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsInComposite() {
        List<IProject> allProjects = Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
        return getUniqueRootProjects(allProjects);
    }
}
