/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.runconfig.hasCargoProject
import javax.swing.JComponent
import javax.swing.JEditorPane

class CargoToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        guessAndSetupRustProject(project)
        val toolwindowPanel = CargoToolWindowPanel(project)
        val tab = ContentFactory.SERVICE.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
    }
}

private class CargoToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, false) {
    private val cargoTab = CargoToolWindow(project)

    init {
        toolbar = cargoTab.toolbar.component
        cargoTab.toolbar.setTargetComponent(this)
        setContent(cargoTab.content)
    }

    override fun getData(dataId: String): Any? =
        when {
            CargoToolWindow.SELECTED_CARGO_PROJECT.`is`(dataId) -> cargoTab.selectedProject
            PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> cargoTab.treeExpander
            else -> super.getData(dataId)
        }
}

class CargoToolWindow(
    private val project: Project
) {
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("Cargo Toolbar", actionManager.getAction("Rust.Cargo") as DefaultActionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }

    private val projectTree = CargoProjectsTree()
    private val projectStructure = CargoProjectTreeStructure(projectTree, project)

    val treeExpander: TreeExpander = object : DefaultTreeExpander(projectTree) {
        override fun isVisible(event: AnActionEvent): Boolean = super.isVisible(event) && project.hasCargoProject
    }

    val selectedProject: CargoProject? get() = projectTree.selectedProject

    val content: JComponent = ScrollPaneFactory.createScrollPane(projectTree, 0)

    init {
        with(project.messageBus.connect()) {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    ApplicationManager.getApplication().invokeLater {
                        projectStructure.updateCargoProjects(projects.sortedBy { it.manifest })
                    }
                }
            })
        }

        ApplicationManager.getApplication().invokeLater {
            projectStructure.updateCargoProjects(project.cargoProjects.allProjects.sortedBy { it.manifest })
        }
    }

    private fun html(body: String): String = """
        <html>
        <head>
            ${UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())}
            <style>body {background: #${ColorUtil.toHex(UIUtil.getTreeBackground())}; text-align: center; }</style>
        </head>
        <body>
            $body
        </body>
        </html>
    """

    companion object {
        @JvmStatic
        val SELECTED_CARGO_PROJECT: DataKey<CargoProject> = DataKey.create<CargoProject>("SELECTED_CARGO_PROJECT")
    }
}
