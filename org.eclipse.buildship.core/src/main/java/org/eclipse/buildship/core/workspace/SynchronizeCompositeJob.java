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

import org.gradle.tooling.ProgressListener;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.util.predicate.Predicates;
import org.eclipse.buildship.core.util.progress.AsyncHandler;
import org.eclipse.buildship.core.util.progress.DelegatingProgressListener;
import org.eclipse.buildship.core.util.progress.ToolingApiWorkspaceJob;

/**
 * Combines the given Gradle builds into a composite build and synchronizes them with their workspace counterparts.
 */
public class SynchronizeCompositeJob extends ToolingApiWorkspaceJob {
    private final FetchStrategy fetchStrategy;
    private final NewProjectHandler newProjectHandler;
    private final Set<FixedRequestAttributes> buildsToSynchronize;
    private final AsyncHandler beforeSynchronization;

    /**
     * A job that force-refreshes all existing Gradle projects in the workspace, importing new sub-projects.
     * @return the job
     */
    public static SynchronizeCompositeJob newForceRefreshWorkspaceJob() {
        Set<FixedRequestAttributes> buildsToSynchronize = SynchronizeCompositeJob.getBuildsInComposite();
        return new SynchronizeCompositeJob(buildsToSynchronize, NewProjectHandler.IMPORT_AND_MERGE, FetchStrategy.FORCE_RELOAD, AsyncHandler.NO_OP);
    }

    /**
     * A job that refreshes all existing Gradle projects in the workspace. Does not import new sub-projects.
     * @return the job
     */
    public static SynchronizeCompositeJob newRefreshWorkspaceJob() {
        Set<FixedRequestAttributes> buildsToSynchronize = SynchronizeCompositeJob.getBuildsInComposite();
        return new SynchronizeCompositeJob(buildsToSynchronize, NewProjectHandler.DONT_IMPORT, FetchStrategy.LOAD_IF_NOT_CACHED, AsyncHandler.NO_OP);
    }

    /**
     * A job that adds the project given by the {@link FixedRequestAttributes} to the composite and force-refreshes the workspace. The {@link NewProjectHandler} decides how to handle new projects.
     * @return the job
     */
    public static SynchronizeCompositeJob newImportProjectJob(FixedRequestAttributes newProject, NewProjectHandler newProjectHandler, AsyncHandler beforeSynchronization) {
        Set<FixedRequestAttributes> buildsToSynchronize = Sets.newHashSet();
        buildsToSynchronize.addAll(getBuildsInComposite());
        buildsToSynchronize.add(newProject);
        return new SynchronizeCompositeJob(buildsToSynchronize, newProjectHandler, FetchStrategy.FORCE_RELOAD, beforeSynchronization);
    }

    private SynchronizeCompositeJob(Set<FixedRequestAttributes> buildsToSynchronize, NewProjectHandler newProjectHandler, FetchStrategy fetchStrategy, AsyncHandler beforeSynchronization) {
        super("Synchronize Gradle projects with the Eclipse workspace", true);
        this.fetchStrategy = fetchStrategy;
        this.newProjectHandler = newProjectHandler;
        this.buildsToSynchronize = buildsToSynchronize;
        this.beforeSynchronization = beforeSynchronization;
        setUser(true);
    }

    @Override
    protected final void runToolingApiJobInWorkspace(IProgressMonitor monitor) {
        monitor.beginTask("Synchronizing Gradle projects with workspace", 100);
        this.beforeSynchronization.run(new SubProgressMonitor(monitor, 10), getToken());
        IJobManager manager = Job.getJobManager();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        manager.beginRule(workspaceRoot, monitor);
        try {
            if (this.buildsToSynchronize.isEmpty()) {
                return;
            }
            OmniEclipseWorkspace gradleWorkspace = loadEclipseWorkspace(new SubProgressMonitor(monitor, 40));
            CorePlugin.workspaceGradleOperations().synchronizeCompositeBuildWithWorkspace(
                gradleWorkspace,
                this.buildsToSynchronize,
                this.newProjectHandler,
                new SubProgressMonitor(monitor, 50)
                );
        } finally {
            manager.endRule(workspaceRoot);
        }
    }

    private OmniEclipseWorkspace loadEclipseWorkspace(IProgressMonitor monitor) {
        monitor.beginTask("Loading workspace model", IProgressMonitor.UNKNOWN);
        try {
            ProcessStreams streams = CorePlugin.processStreamsProvider().getBackgroundJobProcessStreams();
            List<ProgressListener> listeners = ImmutableList.<ProgressListener> of(new DelegatingProgressListener(monitor));
            TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), streams.getInput(), listeners,
                    ImmutableList.<org.gradle.tooling.events.ProgressListener> of(), getToken());
            CompositeModelRepository repository = CorePlugin.modelRepositoryProvider().getCompositeModelRepository(this.buildsToSynchronize);
            return repository.fetchEclipseWorkspace(transientAttributes, this.fetchStrategy);
        } finally {
            monitor.done();
        }
    }

    //TODO move this to an interface like "CompositeBuildManager"
    public static Set<FixedRequestAttributes> getBuildsInComposite() {
        return FluentIterable.from(Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects()))
                .filter(Predicates.accessibleGradleProject())
                .transform(new Function<IProject, FixedRequestAttributes>() {

                    @Override
                    public FixedRequestAttributes apply(IProject project) {
                        return CorePlugin.projectConfigurationManager().readProjectConfiguration(project).getRequestAttributes();
                    }
                }).toSet();
    }
}
