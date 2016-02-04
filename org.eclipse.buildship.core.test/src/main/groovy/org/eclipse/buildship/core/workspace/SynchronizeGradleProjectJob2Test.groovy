package org.eclipse.buildship.core.workspace

import com.google.common.collect.ImmutableList
import com.gradleware.tooling.toolingclient.GradleDistribution
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes
import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.GradleProjectBuilder
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.test.fixtures.LegacyEclipseSpockTestHelper
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper
import org.eclipse.buildship.core.util.progress.AsyncHandler
import org.eclipse.buildship.core.util.variable.ExpressionUtils
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class SynchronizeGradleProjectJob2Test extends Specification {

    @Rule
    TemporaryFolder tempFolder

    def cleanup() {
        CorePlugin.workspaceOperations().deleteAllProjects(null)
    }

    def "Project import job creates a new project in the workspace"(boolean projectDescriptorExists) {
        setup:
        def applyJavaPlugin = false
        File projectLocation = newProject(projectDescriptorExists, applyJavaPlugin)
        ImportGradleProjectJob job = newRefreshGradleProjectJob(projectLocation)

        when:
        job.schedule()
        job.join()

        then:
        CorePlugin.workspaceOperations().findProjectByName(projectLocation.name).present

        where:
        projectDescriptorExists << [false, true]
    }

    def "Project descriptors should be created iff they don't already exist"(boolean applyJavaPlugin, boolean projectDescriptorExists, String descriptorComment) {
        setup:
        File rootProject = newProject(projectDescriptorExists, applyJavaPlugin)
        ImportGradleProjectJob job = newRefreshGradleProjectJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        new File(rootProject, '.project').exists()
        new File(rootProject, '.classpath').exists() == applyJavaPlugin
        CorePlugin.workspaceOperations().findProjectInFolder(rootProject, null).get().getComment() == descriptorComment

        where:
        applyJavaPlugin | projectDescriptorExists | descriptorComment
        false           | false                   | 'Project simple-project created by Buildship.' // the comment from the generated descriptor
        false           | true                    | 'original'                                     // the comment from the original descriptor
        true            | false                   | 'Project simple-project created by Buildship.'
        true            | true                    | 'original'
    }

    def "Imported projects always have Gradle builder and nature"(boolean projectDescriptorExists) {
        setup:
        File rootProject = newProject(projectDescriptorExists, false)
        ImportGradleProjectJob job = newRefreshGradleProjectJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        def project = CorePlugin.workspaceOperations().findProjectByName(rootProject.name).get()
        GradleProjectNature.INSTANCE.isPresentOn(project)
        project.description.buildSpec.find { it.getBuilderName().equals(GradleProjectBuilder.INSTANCE.ID) }

        where:
        projectDescriptorExists << [false, true]
    }

    def "Imported parent projects have filters to hide the content of the children and the build folders"() {
        setup:
        File rootProject = newMultiProject()
        ImportGradleProjectJob job = newRefreshGradleProjectJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        def filters = CorePlugin.workspaceOperations().findProjectByName(rootProject.name).get().getFilters()
        filters.length == 3
        (filters[0].fileInfoMatcherDescription.arguments as String).endsWith('subproject')
        (filters[1].fileInfoMatcherDescription.arguments as String).endsWith('build')
        (filters[2].fileInfoMatcherDescription.arguments as String).endsWith('.gradle')
    }

    def "Importing a project twice won't result in duplicate filters"() {
        setup:
        def workspaceOperations = CorePlugin.workspaceOperations()
        File rootProject = newMultiProject()

        when:
        ImportGradleProjectJob job = newRefreshGradleProjectJob(rootProject)
        job.schedule()
        job.join()

        workspaceOperations.deleteAllProjects(null)

        job = newRefreshGradleProjectJob(rootProject)
        job.schedule()
        job.join()

        then:
        def filters = workspaceOperations.findProjectByName(rootProject.name).get().getFilters()
        filters.length == 3
        (filters[0].fileInfoMatcherDescription.arguments as String).endsWith('subproject')
        (filters[1].fileInfoMatcherDescription.arguments as String).endsWith('build')
        (filters[2].fileInfoMatcherDescription.arguments as String).endsWith('.gradle')
    }

    def "Can import deleted project located in default location"() {
        setup:
        def workspaceOperations = CorePlugin.workspaceOperations()
        def workspaceRootLocation = LegacyEclipseSpockTestHelper.workspace.root.location.toFile()
        def location = new File(workspaceRootLocation, 'projectname')
        location.mkdirs()

        def project = workspaceOperations.createProject("projectname", location, ImmutableList.of(), new NullProgressMonitor())
        project.delete(false, true, new NullProgressMonitor())

        when:
        ImportGradleProjectJob job = newRefreshGradleProjectJob(new File(workspaceRootLocation, "projectname"))
        job.schedule()
        job.join()

        then:
        workspaceOperations.allProjects.size() == 1
    }

    def "Can import project located in workspace folder and with custom root name"() {
        setup:
        File rootProject = newProjectWithCustomNameInWorkspaceFolder()
        ImportGradleProjectJob job = newRefreshGradleProjectJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        LegacyEclipseSpockTestHelper.workspace.root.projects.length == 1
        def project = LegacyEclipseSpockTestHelper.workspace.root.projects[0]
        def locationExpression = ExpressionUtils.encodeWorkspaceLocation(project)
        def decodedLocation = ExpressionUtils.decode(locationExpression)
        rootProject.equals(new File(decodedLocation))

        cleanup:
        rootProject.deleteDir()
    }

    def "Duplicate project names are de-duplicated"() {
        setup:
        def projectA = tempFolder.newFolder('projectA')
        new File(projectA, 'settings.gradle') << "rootProject.name = 'foo'"
        def projectB = tempFolder.newFolder('projectB')
        new File(projectB, 'settings.gradle') << "rootProject.name = 'foo'"
        def importA = newRefreshGradleProjectJob(projectA)
        def importB = newRefreshGradleProjectJob(projectB)

        when:
        importA.schedule()
        importA.join()
        importB.schedule()
        importB.join()

        then:
        def projects = LegacyEclipseSpockTestHelper.workspace.root.projects
        projects.length == 2
        projects*.name as Set == ['foo1', 'foo2'] as Set
    }

    def "Projects can be renamed in cycles"() {
        setup:
        def projectA = tempFolder.newFolder('projectA')
        def projectB = tempFolder.newFolder('projectB')
        def importA = newRefreshGradleProjectJob(projectA)
        def importB = newRefreshGradleProjectJob(projectB)
        def refreshWorkspace = new RefreshGradleProjectsJob()

        when:
        importA.schedule()
        importA.join()
        importB.schedule()
        importB.join()
        new File(projectA, 'settings.gradle') << "rootProject.name = 'projectB'"
        new File(projectB, 'settings.gradle') << "rootProject.name = 'projectA'"
        refreshWorkspace.schedule()
        refreshWorkspace.join()

        then:
        def workspaceRoot = LegacyEclipseSpockTestHelper.workspace.root
        def projects = workspaceRoot.projects
        projects.length == 2
        workspaceRoot.getProject('projectA').getLocation().lastSegment() == "projectB"
        workspaceRoot.getProject('projectB').getLocation().lastSegment() == "projectA"
    }

    def "Project dependencies stay intact when renaming a project"() {
        setup:
        ['projectA', 'projectB'].each { name ->
            def root = tempFolder.newFolder(name)
            new File(root, 'settings.gradle') << "include 'sub1', 'sub2'"
            new File(root, 'build.gradle') << """
                subprojects {
                    apply plugin: 'java'
                }
                project('sub2') {
                    dependencies {
                        compile project(':sub1')
                    }
                }
            """
            new File(root, 'sub1/src/main/java').mkdirs()
            new File(root, 'sub2/src/main/java').mkdirs()
            def importRoot = newRefreshGradleProjectJob(root)
            importRoot.schedule()
            importRoot.join()
        }

        when:
        def refreshWorkspace = new RefreshGradleProjectsJob()
        refreshWorkspace.schedule()
        refreshWorkspace.join()

        then:
        def workspaceRoot = LegacyEclipseSpockTestHelper.workspace.root
        def sub2= workspaceRoot.getProject('projectA-sub2')
        JavaCore.create(sub2).resolvedClasspath.find { entry ->
            entry.entryKind == IClasspathEntry.CPE_PROJECT && entry.path.toString() == '/projectA-sub1'
        }
    }

    def newProject(boolean projectDescriptorExists, boolean applyJavaPlugin) {
        def root = tempFolder.newFolder('simple-project')
        new File(root, 'build.gradle') << (applyJavaPlugin ? 'apply plugin: "java"' : '')
        new File(root, 'settings.gradle') << ''
        new File(root, 'src/main/java').mkdirs()

        if (projectDescriptorExists) {
            new File(root, '.project') << '''<?xml version="1.0" encoding="UTF-8"?>
                <projectDescription>
                  <name>simple-project</name>
                  <comment>original</comment>
                  <projects></projects>
                  <buildSpec></buildSpec>
                  <natures></natures>
                </projectDescription>'''
            if (applyJavaPlugin) {
                new File(root, '.classpath') << '''<?xml version="1.0" encoding="UTF-8"?>
                    <classpath>
                      <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
                      <classpathentry kind="src" path="src/main/java"/>
                      <classpathentry kind="output" path="bin"/>
                    </classpath>'''
            }
        }
        root
    }

    def newMultiProject() {
        def rootProject = tempFolder.newFolder('multi-project')
        new File(rootProject, 'build.gradle') << ''
        new File(rootProject, 'settings.gradle') << 'include "subproject"'
        def subProject = new File(rootProject, "subproject")
        subProject.mkdirs()
        new File(subProject, 'build.gradle') << ''
        rootProject
    }

    def newProjectWithCustomNameInWorkspaceFolder() {
        IWorkspace workspace = LegacyEclipseSpockTestHelper.workspace
        IPath rootLocation = workspace.root.location
        def root = new File(rootLocation.toFile(), 'Bug472223')
        root.mkdirs()
        new File(root, 'build.gradle') << ''
        new File(root, 'settings.gradle') << "rootProject.name = 'my-project-name-is-different-than-the-folder'"
        root
    }

    def ImportGradleProjectJob newRefreshGradleProjectJob(File location) {
        def distribution = GradleDistributionWrapper.from(GradleDistribution.fromBuild()).toGradleDistribution()
        def rootRequestAttributes = new FixedRequestAttributes(location, null, distribution, null, ImmutableList.of(), ImmutableList.of())
        new ImportGradleProjectJob(rootRequestAttributes, [], AsyncHandler.NO_OP)
    }

}
