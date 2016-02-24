/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.core.workspace;

import java.io.File;

import org.gradle.api.specs.Spec;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;

import org.eclipse.buildship.core.gradle.Specs;

/**
 * A base class for {@link NewProjectHandler}s that apply their own logic only if the given
 * {@link OmniEclipseProject} is part of a specific Gradle build. All other calls are passed to the
 * delegate.
 *
 * @author Stefan Oehme
 */
public abstract class BuildSpecificNewProjectHandler extends DelegatingNewProjectHandler {

    private final Spec<OmniEclipseProject> projectInBuild;

    public BuildSpecificNewProjectHandler(File rootProjectDir, NewProjectHandler delegate) {
        super(delegate);
        this.projectInBuild = Specs.eclipseProjectIsSubProjectOf(rootProjectDir);
    }

    @Override
    public final boolean shouldImport(OmniEclipseProject projectModel) {
        if (isPartOfBuild(projectModel)) {
            return shouldImportProjectInBuild(projectModel);
        } else {
            return super.shouldImport(projectModel);
        }
    }

    protected boolean shouldImportProjectInBuild(OmniEclipseProject projectModel) {
        return super.shouldImport(projectModel);
    }

    @Override
    public final boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel) {
        if (isPartOfBuild(projectModel)) {
            return shouldOverwriteDescriptorOfProjectInBuild(descriptor, projectModel);
        } else {
            return super.shouldOverwriteDescriptor(descriptor, projectModel);
        }
    }

    protected boolean shouldOverwriteDescriptorOfProjectInBuild(IProjectDescription descriptor, OmniEclipseProject projectModel) {
        return super.shouldOverwriteDescriptor(descriptor, projectModel);
    }

    @Override
    public final void afterImport(IProject project, OmniEclipseProject projectModel) {
        if (isPartOfBuild(projectModel)) {
            afterImportOfProjectInBuild(project, projectModel);
        } else {
            super.afterImport(project, projectModel);
        }
    }

    protected void afterImportOfProjectInBuild(IProject project, OmniEclipseProject projectModel) {
        super.afterImport(project, projectModel);
    }

    private boolean isPartOfBuild(OmniEclipseProject projectModel) {
        return this.projectInBuild.isSatisfiedBy(projectModel);
    }

}
