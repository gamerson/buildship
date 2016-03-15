/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 *     Simon Scholz <simon.scholz@vogella.com> - Bug 473348
 */

package org.eclipse.buildship.core.workspace.internal;

import java.io.File;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.gradleware.tooling.toolingmodel.OmniEclipseGradleBuild;
import com.gradleware.tooling.toolingmodel.OmniEclipseProject;
import com.gradleware.tooling.toolingmodel.OmniGradleProject;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;
import com.gradleware.tooling.toolingmodel.util.Maybe;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.GradlePluginsRuntimeException;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.gradle.Specs;
import org.eclipse.buildship.core.util.file.RelativePathUtils;
import org.eclipse.buildship.core.util.predicate.Predicates;
import org.eclipse.buildship.core.workspace.GradleClasspathContainer;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.buildship.core.workspace.WorkspaceGradleOperations;

/**
 * Default implementation of the {@link WorkspaceGradleOperations} interface.
 */
public final class DefaultWorkspaceGradleOperations implements WorkspaceGradleOperations {

    @Override
    public void synchronizeGradleBuildWithWorkspace(final OmniEclipseGradleBuild gradleBuild, final FixedRequestAttributes rootRequestAttributes, final NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        try {
            JavaCore.run(new IWorkspaceRunnable() {
                @Override
                public void run(IProgressMonitor monitor) throws CoreException {
                    atomicallySynchronizeGradleBuildWithWorkspace(gradleBuild, rootRequestAttributes, newProjectHandler, monitor);
                }
            }, monitor);
        } catch (CoreException e) {
            throw new GradlePluginsRuntimeException(e);
        }
    }

    private void atomicallySynchronizeGradleBuildWithWorkspace(OmniEclipseGradleBuild gradleBuild, FixedRequestAttributes rootRequestAttributes,
            NewProjectHandler newProjectHandler, IProgressMonitor monitor) {
        // collect Gradle projects and Eclipse workspace projects to sync
        List<OmniEclipseProject> allGradleProjects = gradleBuild.getRootEclipseProject().getAll();
        List<IProject> decoupledWorkspaceProjects = collectOpenWorkspaceProjectsRemovedFromGradleBuild(allGradleProjects, rootRequestAttributes);
        SubMonitor subMonitor = SubMonitor.convert(monitor, "Synchronize Gradle build with workspace", decoupledWorkspaceProjects.size() + allGradleProjects.size());
        try {
            // uncouple the open workspace projects that do not have a corresponding Gradle project anymore
            for (IProject project : decoupledWorkspaceProjects) {
                uncoupleWorkspaceProjectFromGradle(project, subMonitor.newChild(1));
            }
            // synchronize the Gradle projects with their corresponding workspace projects
            for (OmniEclipseProject gradleProject : allGradleProjects) {
                synchronizeGradleProjectWithWorkspaceProject(gradleProject, gradleBuild, rootRequestAttributes, newProjectHandler, subMonitor.newChild(1));
            }
        } finally {
            monitor.done();
        }
    }

    private List<IProject> collectOpenWorkspaceProjectsRemovedFromGradleBuild(List<OmniEclipseProject> gradleProjects, final FixedRequestAttributes rootRequestAttributes) {
        // in the workspace, find all projects with a Gradle nature that belong to the same Gradle build (based on the root project directory) but
        // which do not match the location of one of the Gradle projects of that build
        final Set<File> gradleProjectDirectories = FluentIterable.from(gradleProjects).transform(new Function<OmniEclipseProject, File>() {

            @Override
            public File apply(OmniEclipseProject gradleProject) {
                return gradleProject.getProjectDirectory();
            }
        }).toSet();

        ImmutableList<IProject> allWorkspaceProjects = CorePlugin.workspaceOperations().getAllProjects();
        return FluentIterable.from(allWorkspaceProjects).filter(Predicates.accessibleGradleProject()).filter(new Predicate<IProject>() {

            @Override
            public boolean apply(IProject project) {
                ProjectConfiguration projectConfiguration = CorePlugin.projectConfigurationManager().readProjectConfiguration(project);
                return projectConfiguration.getRequestAttributes().getProjectDir().equals(rootRequestAttributes.getProjectDir()) &&
                        (project.getLocation() == null || !gradleProjectDirectories.contains(project.getLocation().toFile()));
            }
        }).toList();
    }

    private void synchronizeGradleProjectWithWorkspaceProject(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, FixedRequestAttributes rootRequestAttributes, NewProjectHandler newProjectHandler, SubMonitor monitor) {
        monitor.setWorkRemaining(1);
        monitor.subTask(String.format("Synchronize Gradle project %s with workspace project", project.getName()));
        try {
            // check if a project already exists in the workspace at the location of the Gradle project to import
            Optional<IProject> workspaceProject = CorePlugin.workspaceOperations().findProjectByLocation(project.getProjectDirectory());
            if (workspaceProject.isPresent()) {
                synchronizeWorkspaceProject(project, gradleBuild, workspaceProject.get(), rootRequestAttributes, monitor.newChild(1, SubMonitor.SUPPRESS_ALL_LABELS));
            } else {
                if (newProjectHandler.shouldImport(project)) {
                    synchronizeNonWorkspaceProject(project, gradleBuild, rootRequestAttributes, newProjectHandler, monitor.newChild(1, SubMonitor.SUPPRESS_ALL_LABELS));
                } else {
                    monitor.worked(1);
                }
            }
        } catch (CoreException e) {
            throw new GradlePluginsRuntimeException(String.format("Cannot synchronize Gradle project %s with workspace project.", project.getName()), e);
        } finally {
            monitor.done();
        }
    }

    private void synchronizeWorkspaceProject(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, IProject workspaceProject, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is already in the workspace", project.getName()), 1);
        try {
            // check if the workspace project is open or not
            if (workspaceProject.isAccessible()) {
                synchronizeOpenWorkspaceProject(project, gradleBuild, workspaceProject, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
            } else {
                synchronizeClosedWorkspaceProject();
                monitor.worked(1);
            }
        } finally {
            monitor.done();
        }
    }

    private void synchronizeOpenWorkspaceProject(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, IProject workspaceProject, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is open in the workspace", project.getName()), 12);
        try {

            // sync the Eclipse project with the file system first
            CorePlugin.workspaceOperations().refreshProject(workspaceProject, new SubProgressMonitor(monitor, 1));

            workspaceProject = updateProjectName(workspaceProject, project, gradleBuild, new SubProgressMonitor(monitor, 1));

            // add Gradle nature, if needed
            CorePlugin.workspaceOperations().addNature(workspaceProject, GradleProjectNature.ID, new SubProgressMonitor(monitor, 1));

            // persist the Gradle-specific configuration in the Eclipse project's .settings folder, if the configuration is available
            if (rootRequestAttributes != null) {
                ProjectConfiguration configuration = ProjectConfiguration.from(rootRequestAttributes, project);
                CorePlugin.projectConfigurationManager().saveProjectConfiguration(configuration, workspaceProject);
            }

            // update linked resources
            LinkedResourcesUpdater.update(workspaceProject, project.getLinkedResources(), new SubProgressMonitor(monitor, 1));

            // mark derived folders
            markDerivedFolders(project, workspaceProject, new SubProgressMonitor(monitor, 1));

            if (isJavaProject(project)) {
                IJavaProject javaProject;
                if (hasJavaNature(workspaceProject)) {
                    javaProject = JavaCore.create(workspaceProject);
                    monitor.worked(1);
                } else {
                    IPath jrePath = JavaRuntime.getDefaultJREContainerEntry().getPath();
                    IClasspathEntry classpathContainer = GradleClasspathContainer.newClasspathEntry();
                    javaProject = CorePlugin.workspaceOperations().createJavaProject(workspaceProject, jrePath, classpathContainer, new SubProgressMonitor(monitor, 1));
                }
                JavaSourceSettingsUpdater.update(javaProject, project.getJavaSourceSettings().get(), new SubProgressMonitor(monitor, 1));
                SourceFolderUpdater.update(javaProject, project.getSourceDirectories(), new SubProgressMonitor(monitor, 1));
                ClasspathContainerUpdater.updateFromModel(javaProject, project, new SubProgressMonitor(monitor, 1));
            } else {
                monitor.worked(4);
            }

            // set project natures and build commands
            ProjectNatureUpdater.update(workspaceProject, project.getProjectNatures(), new SubProgressMonitor(monitor, 1));
            BuildCommandUpdater.update(workspaceProject, project.getBuildCommands(), new SubProgressMonitor(monitor, 1));
        } finally {
            monitor.done();
        }
    }

    private IProject updateProjectName(IProject workspaceProject, OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, IProgressMonitor monitor) {
        String newName = normalizeProjectName(project);
        monitor.beginTask(String.format("Updating name of project '%s' to '%s'", workspaceProject.getName(), newName), 2);
        try {
            if (newName.equals(workspaceProject.getName())) {
                monitor.worked(2);
                return workspaceProject;
            } else {
                ensureNameIsFree(project, gradleBuild, new SubProgressMonitor(monitor, 1));
                return CorePlugin.workspaceOperations().renameProject(workspaceProject, newName, new SubProgressMonitor(monitor, 1));
            }
        } finally {
            monitor.done();
        }
    }

    /*
     * If there is already a project with the desired name in the workspace, we will try to move it out of the way.
     *
     * Moving the other project is possible if:
     *
     * - it is part of the same synchronize operation
     * - it has a different name in the Gradle model, so it would be renamed anyway
     * - it is not in the default location (otherwise it can't be renamed)
     * - it is open
     *
     * If any of these conditions are not met, we fail because of a name conflict.
     */
    private void ensureNameIsFree(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, IProgressMonitor monitor) {
        String name = normalizeProjectName(project);
        monitor.beginTask(String.format("Ensuring that name '%s' is free", name), 1);
        try {
            Optional<IProject> possibleDuplicate = CorePlugin.workspaceOperations().findProjectByName(name);
            if (possibleDuplicate.isPresent()) {
                IProject duplicate = possibleDuplicate.get();
                if (isScheduledForRenaming(duplicate, gradleBuild)) {
                    renameTemporarily(duplicate, new SubProgressMonitor(monitor, 1));
                } else {
                    throw new GradlePluginsRuntimeException("A project with the name " + name + " already exists");
                }
            }
        } finally {
            monitor.done();
        }
    }

    private boolean isScheduledForRenaming(IProject duplicate, OmniEclipseGradleBuild gradleBuild) {
        if (!duplicate.isOpen()) {
            return false;
        }
        Optional<OmniEclipseProject> duplicateEclipseProject = gradleBuild.getRootEclipseProject().tryFind(Specs.eclipseProjectMatchesProjectDir(duplicate.getLocation().toFile()));
        if (!duplicateEclipseProject.isPresent()) {
            return false;
        }
        String newName = normalizeProjectName(duplicateEclipseProject.get());
        return !newName.equals(duplicate.getName());
    }

    private void renameTemporarily(IProject duplicate, IProgressMonitor monitor) {
        CorePlugin.workspaceOperations().renameProject(duplicate, duplicate.getName() + "-" + duplicate.getName().hashCode(), monitor);
    }

    private void synchronizeClosedWorkspaceProject() {
        // do not modify closed projects
    }

    private void synchronizeNonWorkspaceProject(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, FixedRequestAttributes rootRequestAttributes, NewProjectHandler newProjectHandler, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Synchronize Gradle project %s that is not yet in the workspace", project.getName()), 2);
        IProject workspaceProject = null;
        try {
            // check if an Eclipse project already exists at the location of the Gradle project to import
            Optional<IProjectDescription> projectDescription = CorePlugin.workspaceOperations().findProjectInFolder(project.getProjectDirectory(), new SubProgressMonitor(monitor, 1));
            if (projectDescription.isPresent()) {
                if (newProjectHandler.shouldOverwriteDescriptor(projectDescription.get(), project)) {
                    CorePlugin.workspaceOperations().deleteProjectDescriptors(project.getProjectDirectory());
                    workspaceProject = addNewEclipseProjectToWorkspace(project, gradleBuild, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
                } else {
                    workspaceProject = addExistingEclipseProjectToWorkspace(project, gradleBuild, projectDescription.get(), rootRequestAttributes, new SubProgressMonitor(monitor, 1));
                }
            } else {
                workspaceProject = addNewEclipseProjectToWorkspace(project, gradleBuild, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
            }
        } finally {
            monitor.done();
        }
        newProjectHandler.afterImport(workspaceProject, project);
    }

    private IProject addExistingEclipseProjectToWorkspace(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, IProjectDescription projectDescription, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add existing Eclipse project %s for Gradle project %s to the workspace", projectDescription.getName(), project.getName()), 3);
        try {
            ensureNameIsFree(project, gradleBuild, new SubProgressMonitor(monitor, 1));
            IProject workspaceProject = CorePlugin.workspaceOperations().includeProject(projectDescription, ImmutableList.<String>of(), new SubProgressMonitor(monitor, 1));
            synchronizeOpenWorkspaceProject(project, gradleBuild, workspaceProject, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
            return workspaceProject;
        } finally {
            monitor.done();
        }
    }

    private IProject addNewEclipseProjectToWorkspace(OmniEclipseProject project, OmniEclipseGradleBuild gradleBuild, FixedRequestAttributes rootRequestAttributes, IProgressMonitor monitor) throws CoreException {
        monitor.beginTask(String.format("Add new Eclipse project for Gradle project %s to the workspace", project.getName()), 3);
        try {
            ensureNameIsFree(project, gradleBuild, new SubProgressMonitor(monitor, 1));
            IProject workspaceProject = CorePlugin.workspaceOperations().createProject(project.getName(), project.getProjectDirectory(), ImmutableList.<String>of(), new SubProgressMonitor(monitor, 1));
            synchronizeOpenWorkspaceProject(project, gradleBuild, workspaceProject, rootRequestAttributes, new SubProgressMonitor(monitor, 1));
            return workspaceProject;
        } finally {
            monitor.done();
        }
    }

    private String normalizeProjectName(OmniEclipseProject project) {
        return CorePlugin.workspaceOperations().normalizeProjectName(project.getName(), project.getProjectDirectory());
    }

    private List<IFolder> getSubProjectFolders(OmniEclipseProject project, final IProject workspaceProject) {
        return FluentIterable.from(project.getChildren()).transform(new Function<OmniEclipseProject, IFolder>() {

            @Override
            public IFolder apply(OmniEclipseProject childProject) {
                File dir = childProject.getProjectDirectory();
                IPath relativePath = RelativePathUtils.getRelativePath(workspaceProject.getLocation(), new Path(dir.getPath()));
                return workspaceProject.getFolder(relativePath);
            }
        }).toList();
    }

    private void markDerivedFolders(OmniEclipseProject gradleProject, IProject workspaceProject, IProgressMonitor monitor) throws CoreException {
        List<IFolder> derivedResources = Lists.newArrayList();

        IFolder buildDirectory = getBuildDirectory(gradleProject, workspaceProject);
        derivedResources.add(buildDirectory);
        if (buildDirectory.exists()) {
            CorePlugin.workspaceOperations().markAsBuildFolder(buildDirectory);
        }

        IFolder dotGradle = workspaceProject.getFolder(".gradle");
        derivedResources.add(dotGradle);

        for (IFolder subProjectFolder : getSubProjectFolders(gradleProject, workspaceProject)) {
            derivedResources.add(subProjectFolder);
            if (subProjectFolder.exists()) {
                CorePlugin.workspaceOperations().markAsSubProject(subProjectFolder);
            }
        }

        DerivedResourcesUpdater.updateDerivedResources(workspaceProject, derivedResources, monitor);
    }

    private IFolder getBuildDirectory(OmniEclipseProject project, IProject workspaceProject) {
       OmniGradleProject gradleProject = project.getGradleProject();
        Maybe<File> buildDirectory = gradleProject.getBuildDirectory();
        if (buildDirectory.isPresent() && buildDirectory.get() != null) {
            IPath relativePath = RelativePathUtils.getRelativePath(workspaceProject.getLocation(), new Path(buildDirectory.get().getPath()));
            return workspaceProject.getFolder(relativePath);
        } else {
            return workspaceProject.getFolder("build");
        }
    }

    private boolean isJavaProject(OmniEclipseProject project) {
        return project.getJavaSourceSettings().isPresent();
    }

    private boolean hasJavaNature(IProject project) {
        try {
            return project.hasNature(JavaCore.NATURE_ID);
        } catch (CoreException e) {
            return false;
        }
    }

    private void uncoupleWorkspaceProjectFromGradle(IProject workspaceProject, SubMonitor monitor) {
        monitor.setWorkRemaining(2);
        monitor.subTask(String.format("Uncouple workspace project %s from Gradle", workspaceProject.getName()));
        try {
            CorePlugin.workspaceOperations().removeNature(workspaceProject, GradleProjectNature.ID, monitor.newChild(1, SubMonitor.SUPPRESS_ALL_LABELS));
            DerivedResourcesUpdater.clearDerivedResources(workspaceProject, monitor.newChild(1, SubMonitor.SUPPRESS_ALL_LABELS));
            CorePlugin.projectConfigurationManager().deleteProjectConfiguration(workspaceProject);
        } finally {
            monitor.done();
        }
    }

}
