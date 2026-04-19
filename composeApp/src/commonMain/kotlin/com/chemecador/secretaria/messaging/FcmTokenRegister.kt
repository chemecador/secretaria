package com.chemecador.secretaria.messaging

interface FcmTokenRegister {
    suspend fun registerCurrentToken(): Result<Unit>
    suspend fun unregisterCurrentToken(): Result<Unit>
}

class NoopFcmTokenRegister : FcmTokenRegister {
    override suspend fun registerCurrentToken(): Result<Unit> = Result.success(Unit)
    override suspend fun unregisterCurrentToken(): Result<Unit> = Result.success(Unit)
}
