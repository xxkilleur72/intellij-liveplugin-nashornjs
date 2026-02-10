import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.*
import static liveplugin.PluginUtil.*

def nashornToGroovyAction = new AnAction("Convert Nashorn to Groovy") {
    @Override
    void actionPerformed(AnActionEvent event) {
        def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null) return

        ApplicationManager.application.runWriteAction {
            if (file.isDirectory()) {
                // If it's a folder, walk through it
                VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
                    @Override
                    boolean visitFile(VirtualFile child) {
                        if (child.name.endsWith(".nashorn.js")) processFile(child)
                        return true
                    }
                })
            } else if (file.name.endsWith(".nashorn.js")) {
                // If it's just one file, process only that one
                processFile(file)
            }
        }
    }

    @Override
    void update(AnActionEvent event) {
        def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        // Show if it's a directory OR if it's a Nashorn file
        boolean isNashorn = file != null && (file.isDirectory() || file.name.endsWith(".nashorn.js"))
        event.presentation.enabledAndVisible = isNashorn
    }

    @Override
    ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT }
}

def addMethodsToGroovyScript = new AnAction("Add Methods to Groovy Scripts") {
    @Override
    void actionPerformed(AnActionEvent event) {
        def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null) return

        // Your multiline string
        String methods = """//-------------------METHODS------------------
def method1() { return method2() }
def method2() { return x }
def method3() { return [] }
//-------------------END/METHODS---------------"""

        ApplicationManager.application.runWriteAction {
            if (file.isDirectory()) {
                VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
                    @Override
                    boolean visitFile(VirtualFile child) {
                        // Targeted all .groovy files
                        if (child.name.endsWith(".groovy")) {
                            appendMethodsToFile(child, methods)
                        }
                        return true
                    }
                })
            } else if (file.name.endsWith(".groovy")) {
                appendMethodsToFile(file, methods)
            }
        }
    }

    @Override
    void update(AnActionEvent event) {
        def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        // Show if folder or any .groovy file
        boolean isGroovy = file != null && (file.isDirectory() || file.name.endsWith(".groovy"))
        event.presentation.enabledAndVisible = isGroovy
    }

    @Override
    ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT }
}

// Add to the right-click menu
ActionManager.instance.getAction("ProjectViewPopupMenu").add(nashornToGroovyAction, Constraints.FIRST)
ActionManager.instance.getAction("ProjectViewPopupMenu").add(addMethodsToGroovyScript, Constraints.FIRST)

void processFile(VirtualFile file) {
    file.copy(this, file.parent, file.name + ".old")
    file.rename(this, file.name.replace(".nashorn.js", ".groovy"))
}

// Helper to handle the reading and writing
void appendMethodsToFile(VirtualFile file, String methodsToAdd) {
    try {
        String currentContent = VfsUtilCore.loadText(file)

        def signatures = ["def getV", "def getVD", "def makeC"]

        boolean alreadyExists = signatures.any { currentContent.contains(it) }

        // Check if methods already exist to avoid double-appending
        if (!alreadyExists) {
            String newContent = currentContent + "\n" + methodsToAdd
            VfsUtil.saveText(file, newContent)
            println "Updated: ${file.name}"
        } else {
            println "Skipped: ${file.name} (Methods already present)"
        }
    } catch (Exception e) {
        println "Error processing ${file.name}: ${e.message}"
    }
}