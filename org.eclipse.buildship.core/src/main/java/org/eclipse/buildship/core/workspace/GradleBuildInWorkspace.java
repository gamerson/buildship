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

import java.util.List;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniEclipseWorkspace;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

/**
 * Represents a set of {@link OmniEclipseProject} within an {@link OmniEclipseWorkspace} which
 * belong to the same root project.
 */
public interface GradleBuildInWorkspace {

    /**
     * The projects in the workspace that were derived from the root project specified by
     * {@link #getRequestAttributes()}.
     */
    List<OmniEclipseProject> getEclipseProjects();

    /**
     * The workspace that this build is a part of.
     */
    OmniEclipseWorkspace getWorkspace();

    /**
     * The request attributes that were used to fetch the Eclipse projects of this build.
     */
    FixedRequestAttributes getRequestAttributes();

}
