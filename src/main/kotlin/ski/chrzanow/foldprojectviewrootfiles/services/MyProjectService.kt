package ski.chrzanow.foldprojectviewrootfiles.services

import ski.chrzanow.foldprojectviewrootfiles.FoldProjectViewRootFilesBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(FoldProjectViewRootFilesBundle.message("projectService", project.name))
    }
}
