package org.ic.tech.main

import platform.UIKit.UIDevice
import platform.CoreNFC.NFCISO7816TagProtocol

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()
