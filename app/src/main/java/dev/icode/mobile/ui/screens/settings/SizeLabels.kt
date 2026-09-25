package dev.icode.mobile.ui.screens.settings

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.StorageMeasurement

/** A measured size in the words the Storage and Runtime screens both use. */
internal fun storageSizeLabel(measurement: StorageMeasurement?): String = when {
    measurement == null -> "Measuring…"
    measurement.truncated -> "Size exceeds scan limit"
    measurement.bytes == null -> "Size unavailable"
    else -> ByteSize.format(measurement.bytes)
}
