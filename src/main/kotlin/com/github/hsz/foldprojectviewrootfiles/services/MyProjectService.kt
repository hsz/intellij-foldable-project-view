package com.github.hsz.foldprojectviewrootfiles.services

import com.github.hsz.foldprojectviewrootfiles.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
