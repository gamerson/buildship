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

import org.gradle.jarjar.com.google.common.collect.Sets;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.buildship.core.util.progress.AsyncHandler;

/**
 * Re(-imports) the  given Gradle (multi-)project by force-reloading its configuration and synchronizing it with the Eclipse workspace.
 */
public final class ImportGradleProjectJob extends SynchronizeGradleProjectsJob {

    private final FixedRequestAttributes rootRequestAttributes;
    private final ImmutableList<String> workingSets;
    private final ExistingDescriptorHandler existingDescriptorHandler;
    private final AsyncHandler initializer;

    public ImportGradleProjectJob(FixedRequestAttributes rootRequestAttributes, List<String> workingSets, AsyncHandler initializer) {
        this(rootRequestAttributes, workingSets, ExistingDescriptorHandler.ALWAYS_KEEP, initializer);
    }

    public ImportGradleProjectJob(FixedRequestAttributes rootRequestAttributes, List<String> workingSets, ExistingDescriptorHandler existingDescriptorHandler, AsyncHandler initializer) {
        super(String.format("Synchronize Gradle root project at %s with workspace", Preconditions.checkNotNull(rootRequestAttributes).getProjectDir().getAbsolutePath()), false);

        this.rootRequestAttributes = Preconditions.checkNotNull(rootRequestAttributes);
        this.workingSets = ImmutableList.copyOf(workingSets);
        this.existingDescriptorHandler = Preconditions.checkNotNull(existingDescriptorHandler);
        this.initializer = Preconditions.checkNotNull(initializer);

        // explicitly show a dialog with the progress while the project synchronization is in process
        setUser(true);
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsInComposite() {
        Set<FixedRequestAttributes> builds = Sets.newLinkedHashSet();
        List<IProject> allProjects = Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects());
        builds.addAll(getUniqueRootProjects(allProjects));
        builds.add(this.rootRequestAttributes);
        return builds;
    }

    @Override
    protected Set<FixedRequestAttributes> getBuildsToSynchronize() {
        return getBuildsInComposite();
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
    protected List<String> getWorkingSets(GradleBuildInWorkspace gradleBuild) {
        if (gradleBuild.getRequestAttributes().equals(this.rootRequestAttributes)) {
            return this.workingSets;
        } else {
            return super.getWorkingSets(gradleBuild);
        }
    }

    @Override
    protected void beforeSynchronization(IProgressMonitor progressMonitor) {
        this.initializer.run(new SubProgressMonitor(progressMonitor, 10), getToken());
    }

}
