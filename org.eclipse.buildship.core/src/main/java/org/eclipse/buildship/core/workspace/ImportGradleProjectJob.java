/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.workspace;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.buildship.core.util.progress.AsyncHandler;

/**
 * (Re-)Imports the  given Gradle (multi-)project by force-reloading its configuration and synchronizing it with the Eclipse workspace.
 */
public final class ImportGradleProjectJob extends SynchronizeGradleProjectsJob {

    private final FixedRequestAttributes rootRequestAttributes;
    private final NewProjectHandler newProjectHandler;
    private final ExistingDescriptorHandler existingDescriptorHandler;
    private final AsyncHandler initializer;

    public ImportGradleProjectJob(FixedRequestAttributes rootRequestAttributes, NewProjectHandler newProjectHandler, AsyncHandler initializer) {
        this(rootRequestAttributes, newProjectHandler, ExistingDescriptorHandler.ALWAYS_KEEP, initializer);
    }

    public ImportGradleProjectJob(FixedRequestAttributes rootRequestAttributes, NewProjectHandler newProjectHandler, ExistingDescriptorHandler existingDescriptorHandler, AsyncHandler initializer) {
        super(String.format("Synchronize Gradle root project at %s with workspace", Preconditions.checkNotNull(rootRequestAttributes).getProjectDir().getAbsolutePath()), false, FetchStrategy.FORCE_RELOAD);

        this.rootRequestAttributes = Preconditions.checkNotNull(rootRequestAttributes);
        this.newProjectHandler = newProjectHandler;
        this.existingDescriptorHandler = Preconditions.checkNotNull(existingDescriptorHandler);
        this.initializer = Preconditions.checkNotNull(initializer);

        // explicitly show a dialog with the progress while the project synchronization is in process
        setUser(true);
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsToSynchronize() {
        Set<FixedRequestAttributes> builds = Sets.newLinkedHashSet();
        List<IProject> allProjects = Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
        builds.addAll(getUniqueRootProjects(allProjects));
        builds.add(this.rootRequestAttributes);
        return builds;
    }

    @Override
    protected ExistingDescriptorHandler getExistingDescriptorHandler(GradleBuildInWorkspace gradleBuild) {
        if (gradleBuild.getRequestAttributes().equals(this.rootRequestAttributes)) {
            return this.existingDescriptorHandler;
        } else {
            return super.getExistingDescriptorHandler(gradleBuild);
        }
    }

    @Override
    protected NewProjectHandler getNewProjectHandler(GradleBuildInWorkspace gradleBuild) {
        if (gradleBuild.getRequestAttributes().equals(this.rootRequestAttributes)) {
            return this.newProjectHandler;
        } else {
            return super.getNewProjectHandler(gradleBuild);
        }
    }

    @Override
    protected void beforeSynchronization(IProgressMonitor progressMonitor) {
        this.initializer.run(progressMonitor, getToken());
    }

}
