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

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;

/**
 * A base class for delegating {@link NewProjectHandler} implementations.
 *
 * @author Stefan Oehme
 *
 */
public abstract class DelegatingNewProjectHandler implements NewProjectHandler {

    private final NewProjectHandler delegate;

    public DelegatingNewProjectHandler(NewProjectHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean shouldImport(OmniEclipseProject projectModel) {
        return this.delegate.shouldImport(projectModel);
    }

    @Override
    public boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel) {
        return this.delegate.shouldOverwriteDescriptor(descriptor, projectModel);
    }

    @Override
    public void afterImport(IProject project, OmniEclipseProject projectModel) {
        this.delegate.afterImport(project, projectModel);
    }

}
