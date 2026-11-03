package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File

/** A runtime on a test tree, as iCode's files/ holds one — its root filesystem and its workspaces under [base] — with [name] a workspace in it. */
internal class TestRuntime(base: File, name: String = "app") {
    val rootfs = File(base.canonicalFile, "rootfs").apply { mkdirs(); File(this, "root").mkdirs() }
    val workspaces = File(base.canonicalFile, "workspaces").apply { mkdirs() }
    /** Phone storage, bound where it is (docs/phone-agent-plan.md L8), and `/sdcard` naming it. */
    val shared = File(base.canonicalFile, "storage/emulated/0").apply { mkdirs() }
    val paths = GuestPaths(rootfs, WorkspacePaths(workspaces), shared).also {
        java.nio.file.Files.createSymbolicLink(File(rootfs, "sdcard").toPath(), shared.toPath())
    }
    val root = File(workspaces, name).apply { mkdirs() }
    val workspace = AssistantWorkspace(root, paths)
}
