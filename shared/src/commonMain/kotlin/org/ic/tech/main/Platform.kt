package org.ic.tech.main

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform