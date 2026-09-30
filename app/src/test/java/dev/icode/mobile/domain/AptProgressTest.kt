package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AptProgressTest {
    private fun read(vararg lines: String): AptProgress = lines.fold(AptProgress()) { progress, line -> progress.next(line) }

    /** `apt-get install -d … ripgrep` on the phone, 2026-09-30: apt's plan and download, as printed. */
    private val ripgrepDownload = listOf(
        "Reading package lists...",
        "Building dependency tree...",
        "Reading state information...",
        "The following NEW packages will be installed:",
        "  ripgrep",
        "0 upgraded, 1 newly installed, 0 to remove and 4 not upgraded.",
        "Need to get 1455 kB of archives.",
        "After this operation, 4995 kB of additional disk space will be used.",
        "Get:1 http://ports.ubuntu.com/ubuntu-ports noble/universe arm64 ripgrep arm64 14.1.0-1 [1455 kB]",
        "Fetched 1455 kB in 15s (94.8 kB/s)",
    )

    /**
     * The openssh-client install, 2026-09-30: its plan (six packages, `history.log`) and dpkg's
     * lines as `term.log` recorded them.
     */
    private val sshInstall = listOf(
        "0 upgraded, 6 newly installed, 0 to remove and 12 not upgraded.",
        "Selecting previously unselected package adduser.",
        "(Reading database ... (Reading database ... 5%(Reading database ... 100%(Reading database ... 8053 files and directories currently installed.)",
        "Preparing to unpack .../adduser_3.137ubuntu1_all.deb ...",
        "Unpacking adduser (3.137ubuntu1) ...",
        "Setting up adduser (3.137ubuntu1) ...",
        "Unpacking libbsd0:arm64 (0.12.1-1build1.1) ...",
        "Unpacking libcbor0.10:arm64 (0.10.2-1.2ubuntu2) ...",
        "Unpacking libedit2:arm64 (3.1-20230828-1build1) ...",
        "Unpacking libfido2-1:arm64 (1.14.0-1build3) ...",
        "Unpacking openssh-client (1:9.6p1-3ubuntu13.19) ...",
        "Setting up libcbor0.10:arm64 (0.10.2-1.2ubuntu2) ...",
        "Setting up libfido2-1:arm64 (1.14.0-1build3) ...",
        "Setting up libbsd0:arm64 (0.12.1-1build1.1) ...",
        "Setting up libedit2:arm64 (3.1-20230828-1build1) ...",
        "Setting up openssh-client (1:9.6p1-3ubuntu13.19) ...",
        "Processing triggers for libc-bin (2.39-0ubuntu8.8) ...",
    )

    @Test fun `before apt prints its plan there is nothing to count`() {
        val progress = read("Reading package lists...", "Building dependency tree...")
        assertEquals(AptPhase.Preparing, progress.phase)
        assertEquals("Reading package lists", progress.step)
        assertNull(progress.fraction)
        assertNull(progress.counts)
    }

    @Test fun `the plan gives the total, and downloading says apt's size and the package in hand`() {
        val progress = read(*ripgrepDownload.toTypedArray())
        assertEquals(1, progress.total)
        assertEquals(AptPhase.Downloading, progress.phase)
        assertEquals("ripgrep", progress.item)
        assertEquals("Downloading 1455 kB", progress.step)
        assertNull("a download has no fair share off a terminal", progress.fraction)
    }

    @Test fun `dpkg's lines count the packages set up, and the bar fills by them`() {
        val halfway = read(*sshInstall.take(sshInstall.indexOf("Setting up libfido2-1:arm64 (1.14.0-1build3) ...") + 1).toTypedArray())
        assertEquals(AptPhase.Installing, halfway.phase)
        assertEquals(3, halfway.done)
        assertEquals("libfido2-1", halfway.item)
        assertEquals("3 / 6", halfway.counts)
        assertEquals(0.5f, halfway.fraction)
        assertEquals("Setting up 3 of 6", halfway.step)

        val finished = read(*sshInstall.toTypedArray())
        assertEquals(6, finished.done)
        assertEquals(1f, finished.fraction)
        assertEquals("libc-bin", finished.item)
    }

    @Test fun `unpacking alone is installing, not yet counted`() {
        val progress = read("0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded.", "Unpacking openssh-client (1:9.6p1-3ubuntu13.19) ...")
        assertEquals(AptPhase.Installing, progress.phase)
        assertEquals(0, progress.done)
        assertEquals("openssh-client", progress.item)
    }

    @Test fun `an upgrade and a reinstall count in the total, and an upgrade's unpack names its package`() {
        val progress = read(
            "1 upgraded, 4 newly installed, 1 reinstalled, 0 to remove and 11 not upgraded.",
            "Unpacking libssl3t64:arm64 (3.0.13-0ubuntu3.16) over (3.0.13-0ubuntu3.15) ...",
        )
        assertEquals(6, progress.total)
        assertEquals("libssl3t64", progress.item)
    }

    @Test fun `already fetched archives download nothing`() {
        assertEquals("0 B", read("Need to get 0 B/1455 kB of archives.").downloadSize)
    }

    @Test fun `a removal counts dpkg's removing lines against the plan`() {
        val progress = read(
            "0 upgraded, 0 newly installed, 2 to remove and 12 not upgraded.",
            "(Reading database ... 8801 files and directories currently installed.)",
            "Removing ripgrep (14.1.0-1) ...",
        )
        assertEquals(AptPhase.Removing, progress.phase)
        assertEquals("1 / 2", progress.counts)
        assertEquals("Removing 1 of 2", progress.step)
    }

    /** apt-get update's lines, in apt's format (integration-plan.md records the first). */
    @Test fun `reading the lists fetches lists, not packages`() {
        val progress = read(
            "Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease [256 kB]",
            "Hit:2 http://ports.ubuntu.com/ubuntu-ports noble-updates InRelease",
            "Get:5 http://ports.ubuntu.com/ubuntu-ports noble/main arm64 Packages [1774 kB]",
            "Get:6 http://ports.ubuntu.com/ubuntu-ports noble/universe Translation-en [5982 kB]",
        )
        assertEquals(AptPhase.Updating, progress.phase)
        assertNull(progress.item)
        assertEquals("Updating package lists", progress.step)
    }

    @Test fun `a maintainer script's own words are not dpkg's steps`() {
        val progress = read(
            "0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded.",
            "Removing obsolete conffile /etc/ssh/moduli ...",
            "Setting up openssh-client (1:9.6p1-3ubuntu13.19) ...",
        )
        assertEquals(AptPhase.Installing, progress.phase)
        assertEquals(1, progress.done)
    }

    @Test fun `an archive fetched while dpkg works leaves it installing`() {
        val progress = read(
            "0 upgraded, 1 newly installed, 0 to remove and 4 not upgraded.",
            "Unpacking ripgrep (14.1.0-1) ...",
            "Get:1 http://ports.ubuntu.com/ubuntu-ports noble/universe arm64 ripgrep arm64 14.1.0-1 [1455 kB]",
        )
        assertEquals(AptPhase.Installing, progress.phase)
    }
}
