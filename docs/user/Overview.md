# Current Functionality - A brief overview

This document describes briefly the functionality currently available in Buildship.


## Project Import

You can import an existing Gradle project through the Eclipse Import Wizard functionality. In distinct
steps, the import wizard allows you to specify the location of the Gradle project, configure the various
optional, advanced settings, and see a summary of how the project will be imported. The summary page will
also warn if you are using a target Gradle version for which Buildship is not able to offer all its functionality.

During the import, the Eclipse projects are created and added to the current workspace. In case of Java projects,
the source paths and the classpath container which contains the external dependencies and the project dependencies are
configured. The content of the classpath container is refreshed each time the project is opened.

Projects that already contain a .settings folder are left untouched except the Gradle nature being configured and
the Gradle preferences file being added. This ensures that the given project and its tasks still show up in the Task View.

You can stop the import at any time by pressing the Stop button in the Progress View.


## Project Creation

You can create a new Gradle project and have it be added to the current workspace through the Eclipse New Wizard
functionality. In distinct steps, the creation wizard allows you to specify the name and location of the Gradle
project, and see a summary of how the project will be created and imported.


## Task View

In the Gradle Task View, you can see the tasks of all the imported Gradle projects. The tasks can be sorted, filtered,
and executed. The selection in Eclipse can be key linked to the selection of the task view, both ways. The content of
the task view can be refreshed, meaning the latest versions of the Gradle build files are loaded. You can navigate from
a project to its build file through the context menu.


## Task Execution

You can run Gradle tasks from the Task View through double-clicking, right-clicking, or via keyboard. In case multiple
tasks are selected, they are passed to Gradle in the order they were selected. Each time a Gradle build is executed with a
different set of tasks, a new Gradle run configuration is created. You can edit existing run configurations and create new
run configurations through the Run Configurations dialog. All settings can be configured in the run configurations dialog.

Whenever a Gradle build is executed, a new Gradle console is opened that contains the output from the build. You can cancel
the execution of the build by pressing the Stop button in the Gradle console. The Gradle consoles can be closed individually
or all at once.


## Task Execution Progress

Whenever a Gradle build is executed, a new Execution page is opened in the Executions View that displays the progress of
running the build. You can see the different life-cycle phases of the build, the tasks and tests being run, and the success
of each operation.

You can switch between the execution pages and you can jump to the corresponding Gradle console. You can also rerun a finished
build. You can cancel the execution of the build by pressing the Stop button in the execution page. The execution pages can be
closed individually or all at once.

This is available if the Gradle build is run with target Gradle version 2.5 or newer.


## Test execution


## Cancellation

You can cancel all long-running operations like importing a project, executing tasks, refreshing the tasks, etc.

This is available if the Gradle build is run with target Gradle version 2.1 or newer.
