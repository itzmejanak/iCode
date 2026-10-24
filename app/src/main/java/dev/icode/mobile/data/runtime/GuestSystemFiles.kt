package dev.icode.mobile.data.runtime

import java.io.File

/**
 * What iCode sets in the base system at install and Repair (docs/runtime-contract-plan.md F12),
 * written with the runtime as `sudo` is ([GuestSudo]), each file saying so on its first line
 * ([RuntimeLayout.MANAGED]): what Android leaves out, and what a phone's storage needs.
 */
internal object GuestSystemFiles {
    /** Each file, by its path from the root filesystem, and what it holds. */
    val FILES: Map<String, String> = mapOf(
        // Android gives the guest no resolver configuration; without it apt cannot resolve names.
        "etc/resolv.conf" to file("Android gives the runtime no resolver of its own.", "nameserver 8.8.8.8", "nameserver 8.8.4.4"),
        "etc/hosts" to file("Android gives the runtime no hosts file of its own.", "127.0.0.1 localhost", "::1 localhost ip6-localhost ip6-loopback"),
        // dpkg syncs every file it unpacks, and on the phone's flash those syncs dominate an install
        // (measured: git 152 s with them, 84 s without). The package database is rebuildable —
        // Repair Runtime exists for a lost write — and container images drop the syncs the same
        // way. The standard dpkg.cfg.d place applies it to every dpkg run.
        "etc/dpkg/dpkg.cfg.d/icode-unsafe-io" to file("dpkg skips its syncs, which slow an install on a phone's flash.", "force-unsafe-io"),
        // Android refuses link(), and PRoot's stand-in for a hard link is a symbolic link naming an
        // absolute path: renaming the folder that holds one breaks it, both names at once
        // (measured: a clone moved out of its staging folder read "bad object HEAD"). Git needs no
        // hard link: told to, it renames its finished objects and packs into place — what it does
        // anyway where link() fails. Its system file reaches every repository, iCode's commands and
        // the terminal's alike.
        "etc/gitconfig" to file("Android refuses hard links, so Git renames its objects into place.", "[core]", "\tcreateObject = rename"),
    )

    fun write(rootfs: File) = FILES.forEach { (path, text) -> File(rootfs, path).apply { parentFile?.mkdirs() }.writeText(text) }

    private fun file(why: String, vararg lines: String): String =
        (listOf("# ${RuntimeLayout.MANAGED} $why Written at install and Repair.") + lines).joinToString("\n", postfix = "\n")
}
