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

package org.eclipse.buildship.core.gradle;

import java.util.List;
import java.util.Set;

import org.gradle.tooling.ProgressListener;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;

import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.CompositeModelRepository;
import com.gradleware.tooling.toolingmodel.repository.FetchStrategy;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.repository.ModelRepositoryProvider;
import com.gradleware.tooling.toolingmodel.repository.TransientRequestAttributes;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.console.ProcessStreams;
import org.eclipse.buildship.core.console.ProcessStreamsProvider;
import org.eclipse.buildship.core.util.progress.DelegatingProgressListener;
import org.eclipse.buildship.core.util.progress.ToolingApiJob;

/**
 * Loads the {@link OmniEclipseWorkspace} for the given
 * {@link org.eclipse.buildship.core.configuration.ProjectConfiguration} instances.
 *
 * It is ensured that only one instance of this job can run at any given time.
 */
public final class LoadEclipseWorkspaceJob extends ToolingApiJob {

    private final ModelRepositoryProvider modelRepositoryProvider;
    private final ProcessStreamsProvider processStreamsProvider;
    private final FetchStrategy modelFetchStrategy;
    private final ImmutableSet<ProjectConfiguration> configurations;
    private OmniEclipseWorkspace result;

    public LoadEclipseWorkspaceJob(ModelRepositoryProvider modelRepositoryProvider, ProcessStreamsProvider processStreamsProvider, FetchStrategy modelFetchStrategy,
            Set<ProjectConfiguration> configurations, final FutureCallback<OmniEclipseWorkspace> resultHandler) {
        super("Loading tasks of all projects");
        this.modelRepositoryProvider = Preconditions.checkNotNull(modelRepositoryProvider);
        this.processStreamsProvider = Preconditions.checkNotNull(processStreamsProvider);
        this.modelFetchStrategy = Preconditions.checkNotNull(modelFetchStrategy);
        this.configurations = ImmutableSet.copyOf(configurations);
        addJobChangeListener(new JobChangeAdapter() {

            @Override
            public void done(IJobChangeEvent event) {
                if (event.getResult().isOK()) {
                    resultHandler.onSuccess(LoadEclipseWorkspaceJob.this.result);
                } else {
                    resultHandler.onFailure(event.getResult().getException());
                }
            }
        });
    }

    @Override
    protected void runToolingApiJob(IProgressMonitor monitor) throws Exception {
        try {
            Set<FixedRequestAttributes> requestAttributes = Sets.newLinkedHashSet();
            for (ProjectConfiguration projectConfiguration : this.configurations) {
                requestAttributes.add(projectConfiguration.getRequestAttributes());
            }
            if (requestAttributes.isEmpty()) {
                return;
            }
            this.result = loadEclipseWorkspace(requestAttributes, monitor);
        } finally {
            monitor.done();
        }
    }

    private OmniEclipseWorkspace loadEclipseWorkspace(Set<FixedRequestAttributes> fixedRequestAttributes, IProgressMonitor monitor) {
        monitor.beginTask("Loading workspace model", IProgressMonitor.UNKNOWN);
        try {
            ProcessStreams streams = this.processStreamsProvider.getBackgroundJobProcessStreams();
            List<ProgressListener> listeners = ImmutableList.<ProgressListener> of(new DelegatingProgressListener(monitor));
            TransientRequestAttributes transientAttributes = new TransientRequestAttributes(false, streams.getOutput(), streams.getError(), streams.getInput(), listeners,
                    ImmutableList.<org.gradle.tooling.events.ProgressListener> of(), getToken());
            CompositeModelRepository repository = this.modelRepositoryProvider.getCompositeModelRepository(fixedRequestAttributes);
            return repository.fetchEclipseWorkspace(transientAttributes, this.modelFetchStrategy);
        } finally {
            monitor.done();
        }
    }

    @Override
    public boolean belongsTo(Object family) {
        return getJobFamilyName().equals(family);
    }

    @Override
    public boolean shouldRun() {
        // if another job of this type is already scheduled, then
        // we see 2 jobs by that name in the job manager
        // (the current job gets registered before shouldRun() is called)
        return Job.getJobManager().find(getJobFamilyName()).length <= 1;
    }

    private String getJobFamilyName() {
        return LoadEclipseWorkspaceJob.class.getName();
    }

}
