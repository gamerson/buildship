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

import java.util.List;
import java.util.Set;

import org.gradle.jarjar.com.google.common.collect.Lists;
import org.gradle.tooling.ProgressListener;

import com.google.common.collect.ImmutableList;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.util.progress.DelegatingProgressListener;
import org.eclipse.buildship.core.util.progress.ToolingApiWorkspaceJob;
import org.eclipse.buildship.core.workspace.internal.DefaultGradleBuildInWorkspace;

/**
 * Base class for jobs that synchronize a set of Gradle projects with their workspace counterparts.
 */
public abstract class SynchronizeGradleProjectsJob extends ToolingApiWorkspaceJob {

    public SynchronizeGradleProjectsJob(String description, boolean notifyUserOfBuildFailures) {
        super(description, notifyUserOfBuildFailures);
    }

    @Override
    protected final void runToolingApiJobInWorkspace(IProgressMonitor monitor) {
        monitor.beginTask("Synchronizing Gradle projects with workspace", 100);
        beforeSynchronization(new SubProgressMonitor(monitor, 10));
        IJobManager manager = Job.getJobManager();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        manager.beginRule(workspaceRoot, monitor);
        try {
            Set<FixedRequestAttributes> requestAttributes = getBuildsToSynchronize();
            if (requestAttributes.isEmpty()) {
                return;
            }
            OmniEclipseWorkspace gradleWorkspace = forceRoadEclipseWorkspace(requestAttributes, new SubProgressMonitor(monitor, 40));
            for (FixedRequestAttributes attributes : requestAttributes) {
                GradleBuildInWorkspace gradleBuild = DefaultGradleBuildInWorkspace.from(gradleWorkspace, attributes);
                CorePlugin.workspaceGradleOperations().synchronizeGradleBuildWithWorkspace(
                    gradleBuild,
                    getWorkingSets(gradleBuild),
                    getExistingDescriptorHandler(gradleBuild),
                    new SubProgressMonitor(monitor, 50)
                );
            }
        } finally {
            manager.endRule(workspaceRoot);
        }
    }

    /**
     * Invoked before the synchronization is started.
     */
    protected void beforeSynchronization(IProgressMonitor progressMonitor) {
    }

    /**
     * The request attributes of the root projects to synchronize.
     */
    protected abstract Set<FixedRequestAttributes> getBuildsToSynchronize();

    /**
     * Determines what should happen if a project is newly imported to the workspace, but already had a project descriptor.
     */
    protected ExistingDescriptorHandler getExistingDescriptorHandler(GradleBuildInWorkspace gradleBuild) {
        return ExistingDescriptorHandler.ALWAYS_KEEP;
    }

    /**
     * Determines which working sets to assign to the projects corresponding to the given Gradle build.
     */
    protected List<String> getWorkingSets(GradleBuildInWorkspace gradleBuild) {
        return Lists.<String> newArrayList();
    }

    private OmniEclipseWorkspace forceRoadEclipseWorkspace(Set<FixedRequestAttributes> fixedRequestAttributes, IProgressMonitor monitor) {
        monitor.beginTask("Loading workspace model", IProgressMonitor.UNKNOWN);
        try {
            ProcessStreams streams = CorePlugin.processStreamsProvider().getBackgroundJobProcessStreams();
            List<ProgressListener> listeners = ImmutableList.<ProgressListener> of(new DelegatingProgressListener(monitor));
            TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), streams.getInput(), listeners,
                    ImmutableList.<org.gradle.tooling.events.ProgressListener> of(), getToken());
            CompositeModelRepository repository = CorePlugin.modelRepositoryProvider().getCompositeModelRepository(fixedRequestAttributes.toArray(new FixedRequestAttributes[0]));
            return repository.fetchEclipseWorkspace(transientAttributes, FetchStrategy.FORCE_RELOAD);
        } finally {
            monitor.done();
        }
    }

}
