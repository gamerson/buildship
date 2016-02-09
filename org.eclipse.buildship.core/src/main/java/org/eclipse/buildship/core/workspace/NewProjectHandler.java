/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.core.workspace;

import com.gradleware.tooling.toolingmodel.OmniEclipseProject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;

/**
 * Handler for new projects discovered during synchronization.
 *
 * This handler is called when Buildship's project synchronization finds a new subproject that has
 * not yet been imported into Eclipse. The handler can decide whether to import it, whether to keep
 * existing descriptors and can apply postprocessing to the newly created project.
 *
 * @author Stefan Oehme
 *
 */
public interface NewProjectHandler {

    public static final NewProjectHandler IMPORT_AND_MERGE = new NewProjectHandler() {

        @Override
        public boolean shouldImport(OmniEclipseProject projectModel) {
            return true;
        }

        @Override
        public boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel) {
            return false;
        };

        @Override
        public void afterImport(IProject project, OmniEclipseProject projectModel) {
        }
    };

    public static final NewProjectHandler IMPORT_AND_OVERWRITE = new NewProjectHandler() {

        @Override
        public boolean shouldImport(OmniEclipseProject projectModel) {
            return true;
        }

        @Override
        public boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel) {
            return true;
        }

        @Override
        public void afterImport(IProject project, OmniEclipseProject projectModel) {
        }
    };

    public static final NewProjectHandler DONT_IMPORT = new NewProjectHandler() {

        @Override
        public boolean shouldImport(OmniEclipseProject projectModel) {
            return false;
        }

        @Override
        public boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel) {
            return false;
        }

        @Override
        public void afterImport(IProject project, OmniEclipseProject projectModel) {
        }
    };

    /**
     * Determines whether the given project that was found in the Gradle model should be imported
     * into the workspace.
     *
     * @param projectModel the Gradle model of the project
     * @return true if the project should be imported, false otherwise
     */
    boolean shouldImport(OmniEclipseProject projectModel);

    /**
     * If a non-workspace project already has an existing project descriptor, it can either be
     * overwritten or merged with the information provided by Gradle.
     *
     * @param descriptor the existing project descriptor
     * @param projectModel the Gradle model of the project
     * @return true if the existing descriptor should be overwritten, false otherwise
     */
    boolean shouldOverwriteDescriptor(IProjectDescription descriptor, OmniEclipseProject projectModel);

    /**
     * Called after a project is newly imported into the workspace and all Gradle configuration has
     * been applied.
     *
     * @param project the newly imported project
     * @param projectModel the Gradle model of the project
     */
    void afterImport(IProject project, OmniEclipseProject projectModel);
}
