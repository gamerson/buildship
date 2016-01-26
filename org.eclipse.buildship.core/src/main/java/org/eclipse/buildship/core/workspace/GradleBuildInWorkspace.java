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
     * {@link #getRequestAttributes()}
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
