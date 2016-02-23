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

package org.eclipse.buildship.core.workspace.internal;

import java.util.List;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

import org.eclipse.buildship.core.gradle.Specs;
import org.eclipse.buildship.core.workspace.GradleBuildInWorkspace;

/**
 * Default implementation of {@link GradleBuildInWorkspace}.
 */
public final class DefaultGradleBuildInWorkspace implements GradleBuildInWorkspace {

    private final List<OmniEclipseProject> eclipseProjects;
    private final OmniEclipseWorkspace workspace;
    private final FixedRequestAttributes requestAttributes;

    private DefaultGradleBuildInWorkspace(OmniEclipseWorkspace workspace, FixedRequestAttributes requestAttributes) {
        this.eclipseProjects = workspace.filter(Specs.eclipseProjectIsSubProjectOf(requestAttributes.getProjectDir()));
        this.workspace = workspace;
        this.requestAttributes = requestAttributes;
    }

    @Override
    public List<OmniEclipseProject> getEclipseProjects() {
        return this.eclipseProjects;
    }

    @Override
    public OmniEclipseWorkspace getWorkspace() {
        return this.workspace;
    }

    @Override
    public FixedRequestAttributes getRequestAttributes() {
        return this.requestAttributes;
    }

    public static GradleBuildInWorkspace from(OmniEclipseWorkspace workspace, FixedRequestAttributes requestAttributes) {
        return new DefaultGradleBuildInWorkspace(workspace, requestAttributes);
    }

}
