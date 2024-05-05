package com.pj.foldableprojectview.projectView

import com.intellij.icons.AllIcons.General.CollapseComponent
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN
import com.pj.foldableprojectview.psi.search.FoldableProjectSearchScope
import com.pj.foldableprojectview.settings.FoldableProjectSettings
import com.pj.foldableprojectview.settings.Rule

class FoldableProjectViewNode(
    project: Project,
    private val viewSettings: ViewSettings?,
    private val settings: FoldableProjectSettings,
    private val rule: Rule,
    private val parent: PsiDirectoryNode,
) : ProjectViewNode<String>(project, rule.name, viewSettings), PsiFileSystemItemFilter,
    PsiElementProcessor<PsiFileSystemItem> {

    val containsMatchedChildKey: Key<Boolean> = Key.create("FOLDABLE_PROJECT_VIEW_CONTAINS_MATCHED_CHILD")
    val ruleScope = FoldableProjectSearchScope(project, rule.pattern, settings)

    override fun update(presentation: PresentationData) {
        presentation.apply {
            val textAttributes = SimpleTextAttributes(STYLE_PLAIN, rule.foreground)
            addText(ColoredFragment(rule.name, rule.pattern, textAttributes))
            setIcon(CollapseComponent)
        }
    }

    override fun getName() = rule.name

    override fun toString() = name

    override fun execute(item: PsiFileSystemItem): Boolean {
        val matched = item.matches()

        if (matched) {
//            putUserDataUntilRoot(item.parent, matched)
            item.parent?.putUserData(containsMatchedChildKey, matched)
        }

        return !matched // stop processing other children if this matches
    }
//    private fun putUserDataUntilRoot(item: PsiFileSystemItem?, matched: Boolean) { // replace ItemType with the actual type of item
//        if (item == null || item == project) {
//            return
//        }
//
//        item.putUserData(containsMatchedChildKey, matched)
//        putUserDataUntilRoot(item.parent, matched)
//    }

    override fun shouldShow(item: PsiFileSystemItem): Boolean {
        return item.matches()
    }

    override fun computeBackgroundColor() = rule.background

    override fun getChildren(): MutableCollection<AbstractTreeNode<*>> =
        ProjectViewDirectoryHelper
            .getInstance(myProject)
            .getDirectoryChildren(parent.value, viewSettings, true, this)

    override fun contains(file: VirtualFile) = children.firstOrNull {
        it is ProjectViewNode && it.virtualFile == file
    } != null

    private fun PsiFileSystemItem.matches(): Boolean {
        // check userdata
        if (isDirectory && !settings.matchDirectories) {
            return !processChildren(this@FoldableProjectViewNode)
        }
        return ruleScope.contains(virtualFile) || !processChildren(this@FoldableProjectViewNode) // processChildren returns false if {#execute} found matched child
    }
}
