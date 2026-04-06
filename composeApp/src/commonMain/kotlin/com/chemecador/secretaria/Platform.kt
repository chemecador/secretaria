package com.chemecador.secretaria

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform