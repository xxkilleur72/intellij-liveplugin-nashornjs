import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.*
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import static liveplugin.PluginUtil.*

// --- Configuration ---
class Config {
    static final String NASHORN_JS_EXT = ".nashorn.js"
    static final String GROOVY_EXT = ".groovy"

    static final String SOURCE_EXT = NASHORN_JS_EXT
    static final String TARGET_EXT = GROOVY_EXT
    static final String BACKUP_PATH = "C:/Absolute/Path/To/Your/Backup/Folder"

    static final String[] METHODS_POSSIBLE_SIGNATURES = ["def getV", "def getVD", "def makeC"]
    static final String METHODS_TO_ADD = """//-------------------METHODS------------------
def getV() { return getVD() }
def getVD() { return x }
def makeC() { return [] }
//-------------------END/METHODS---------------"""

    static final Map<String, String> SCRIPT_LANGUAGE_MAP = [
            (NASHORN_JS_EXT) : "Nashorn JavaScript",
            (GROOVY_EXT) : "Groovy"
    ].withDefault { "Unknown" }
}

// --- Action Definitions ---
class ConverterActions {

    static AnAction createGoogleSheetObProgressSheetData() {
        return new AnAction("Export Scripts Data to Clipboard for Google Sheet") {
            @Override
            void actionPerformed(AnActionEvent event) {
                def rootFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
                if (rootFile == null) return

                StringBuilder sb = new StringBuilder()

                iterateFiles(rootFile, "") { child ->
                    String rawName = child.name
                    String matchedExt = null

                    if (rawName.endsWith(Config.NASHORN_JS_EXT)) matchedExt = Config.NASHORN_JS_EXT
                    else if (rawName.endsWith(Config.GROOVY_EXT)) matchedExt = Config.GROOVY_EXT

                    if (matchedExt) {
                        String folderPath = child.parent != null ? child.parent.path : ""
                        String cleanName = rawName.replace(matchedExt, "")
                        String language = Config.SCRIPT_LANGUAGE_MAP[matchedExt]
                        sb.append("${folderPath}\t${language}\t${cleanName}\n")
                    }
                }

                copyToClipboard(sb.toString())
                show "Cleaned data copied to clipboard!"
            }

            @Override
            void update(AnActionEvent event) {
                def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
                event.presentation.enabledAndVisible = file != null && file.isDirectory()
            }

            @Override
            ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT }
        }
    }

    static AnAction convertExtensionFromSourceToTarget() {
        return new AnAction("Convert Nashorn to Groovy") {
            @Override
            void actionPerformed(AnActionEvent event) {
                def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
                if (file == null) return
                ApplicationManager.application.runWriteAction {
                    iterateFiles(file, Config.SOURCE_EXT) { child -> processFile(child) }
                }
            }
            @Override
            void update(AnActionEvent event) {
                def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
                event.presentation.enabledAndVisible = file != null && (file.isDirectory() || file.name.endsWith(Config.SOURCE_EXT))
            }
            @Override
            ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT }
        }
    }

    static AnAction createAddMethodsAction() {
        return new AnAction("Add Methods to Groovy Scripts") {
            @Override
            void actionPerformed(AnActionEvent event) {
                def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
                if (file == null) return
                ApplicationManager.application.runWriteAction {
                    iterateFiles(file, Config.TARGET_EXT) { child -> appendMethodsToFile(child) }
                }
            }
            @Override
            void update(AnActionEvent event) {
                def file = event.getData(CommonDataKeys.VIRTUAL_FILE)
                event.presentation.enabledAndVisible = file != null && (file.isDirectory() || file.name.endsWith(Config.TARGET_EXT))
            }
            @Override
            ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT }
        }
    }

    // --- Internal Logic Helpers ---
    private static void copyToClipboard(String text) {
        def selection = new StringSelection(text)
        Toolkit.defaultToolkit.systemClipboard.setContents(selection, selection)
    }

    private static void iterateFiles(VirtualFile root, String extension, Closure logic) {
        if (root.isDirectory()) {
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
                @Override
                boolean visitFile(VirtualFile child) {
                    if (extension == "" || child.name.endsWith(extension)) logic(child)
                    return true
                }
            })
        } else if (extension == "" || root.name.endsWith(extension)) {
            logic(root)
        }
    }

    private static void processFile(VirtualFile file) {
        try {
            File backupIoFile = new File(Config.BACKUP_PATH)
            if (!backupIoFile.exists()) backupIoFile.mkdirs()

            VirtualFile backupDir = LocalFileSystem.instance.refreshAndFindFileByIoFile(backupIoFile)
            if (backupDir != null) {
                if (backupDir.findChild(file.name) == null) {
                    file.copy(null, backupDir, file.name)
                }
                file.rename(null, file.name.replace(Config.SOURCE_EXT, Config.TARGET_EXT))
            }
        } catch (Exception e) {
            show "Error processing ${file.name}: ${e.message}"
        }
    }

    private static void appendMethodsToFile(VirtualFile file) {
        try {
            String content = VfsUtilCore.loadText(file)
            boolean alreadyHasMethods = Config.METHODS_POSSIBLE_SIGNATURES.any { content.contains(it) }
            if (!alreadyHasMethods) {
                VfsUtil.saveText(file, content + "\n" + Config.METHODS_TO_ADD)
                show "Updated: ${file.name}"
            }
        } catch (Exception e) {
            show "Error updating ${file.name}: ${e.message}"
        }
    }
}

// --- Main Execution (Registration) ---
def popupMenu = ActionManager.instance.getAction("ProjectViewPopupMenu")
popupMenu.add(ConverterActions.convertExtensionFromSourceToTarget(), Constraints.FIRST)
popupMenu.add(ConverterActions.createAddMethodsAction(), Constraints.FIRST)
popupMenu.add(ConverterActions.createGoogleSheetObProgressSheetData(), Constraints.FIRST)