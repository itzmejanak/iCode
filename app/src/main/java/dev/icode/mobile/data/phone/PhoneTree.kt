package dev.icode.mobile.data.phone

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.icode.mobile.domain.phone.PhoneBounds
import dev.icode.mobile.domain.phone.PhoneNode
import dev.icode.mobile.domain.phone.PhoneReading
import dev.icode.mobile.domain.phone.PhoneScreen
import dev.icode.mobile.domain.phone.PhoneWindow

/**
 * The screen read from the service's windows (docs/phone-agent-plan.md §5.2), and the node each element
 * came from, to act on it. A tree is read with prefetching from Android 13, in fewer round trips to the
 * app (§5.4); keyboards and overlays are noted, never read.
 */
internal object PhoneTree {
    /** A screen as read, and its elements' nodes by their paths. */
    class Read(val screen: PhoneScreen, val nodes: Map<List<Int>, AccessibilityNodeInfo>)

    fun read(service: AccessibilityService, id: Int): Read {
        val nodes = HashMap<List<Int>, AccessibilityNodeInfo>()
        val windows = service.windows.mapIndexed { index, window -> window(index, window, nodes) }
        val screen = PhoneReading.screen(id, windows)
        return Read(screen, screen.elements.mapNotNull { element -> nodes[element.path]?.let { element.path to it } }.toMap())
    }

    private fun window(index: Int, window: AccessibilityWindowInfo, nodes: MutableMap<List<Int>, AccessibilityNodeInfo>): PhoneWindow {
        val kind = when (window.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> PhoneWindow.Kind.App
            AccessibilityWindowInfo.TYPE_SYSTEM -> PhoneWindow.Kind.System
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> PhoneWindow.Kind.Keyboard
            else -> PhoneWindow.Kind.Overlay
        }
        val read = kind == PhoneWindow.Kind.App || kind == PhoneWindow.Kind.System
        val root = if (!read) null else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            window.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
        } else {
            window.root
        }
        return PhoneWindow(
            kind = kind,
            app = root?.packageName?.toString().orEmpty(),
            title = window.title?.toString(),
            active = window.isActive,
            root = root?.let { node(it, listOf(index), 0, nodes) },
        )
    }

    private fun node(info: AccessibilityNodeInfo, path: List<Int>, depth: Int, nodes: MutableMap<List<Int>, AccessibilityNodeInfo>): PhoneNode {
        nodes[path] = info
        val bounds = Rect().also(info::getBoundsInScreen)
        val children = if (depth >= PhoneReading.MAX_DEPTH) emptyList() else (0 until info.childCount).mapNotNull { index ->
            info.getChild(index)?.let { node(it, path + index, depth + 1, nodes) }
        }
        @Suppress("DEPRECATION") // getChecked() is Android 16's; isChecked() is on every version iCode runs on.
        val checked = info.isChecked
        return PhoneNode(
            className = info.className?.toString().orEmpty(),
            text = info.text?.toString(),
            description = info.contentDescription?.toString(),
            hint = info.hintText?.toString(),
            viewId = info.viewIdResourceName,
            bounds = PhoneBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            clickable = info.isClickable,
            longClickable = info.isLongClickable,
            editable = info.isEditable,
            scrollable = info.isScrollable,
            checkable = info.isCheckable,
            checked = checked,
            selected = info.isSelected,
            enabled = info.isEnabled,
            focused = info.isFocused,
            password = info.isPassword,
            visible = info.isVisibleToUser,
            children = children,
        )
    }
}
