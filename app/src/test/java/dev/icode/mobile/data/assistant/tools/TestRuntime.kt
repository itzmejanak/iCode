package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File

/** A runtime on a test tree, as iCode's files/ holds one — its root filesystem and its workspaces under [base] — with [name] a workspace in it. */
internal class TestRuntime(base: File, name: String = "app") {
    val rootfs = File(base.canonicalFile, "rootfs").apply { mkdirs() }
    val workspaces = File(base.canonicalFile, "workspaces").apply { mkdirs() }
    val paths = GuestPaths(rootfs, WorkspacePaths(workspaces))
    val root = File(workspaces, name).apply { mkdirs() }
    val workspace = AssistantWorkspace(root, paths)
}
