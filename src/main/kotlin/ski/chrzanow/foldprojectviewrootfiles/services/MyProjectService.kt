package ski.chrzanow.foldprojectviewrootfiles.services

import ski.chrzanow.foldprojectviewrootfiles.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
