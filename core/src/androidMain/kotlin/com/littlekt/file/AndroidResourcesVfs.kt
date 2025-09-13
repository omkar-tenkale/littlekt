package com.littlekt.file

import android.net.Uri
import com.littlekt.AndroidContext
import com.littlekt.core.generated.resources.Res
import com.littlekt.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidResourcesVfs(androidContext: AndroidContext, logger: Logger) :
    LocalVfs(androidContext, logger, baseDir = "") {

    override suspend fun loadRawAsset(rawRef: RawAssetRef): LoadedRawAsset {
        return LoadedRawAsset(rawRef, resourceInputStream(rawRef.url)?.let {
            ByteBufferImpl(it.readBytes())
        })
    }

    override suspend fun loadSequenceStreamAsset(
        sequenceRef: SequenceAssetRef
    ): SequenceStreamCreatedAsset {
        return SequenceStreamCreatedAsset(sequenceRef, resourceInputStream(sequenceRef.url)?.let {
            JvmByteSequenceStream(it)
        })
    }

    private suspend fun resourceInputStream(url: String) = withContext(Dispatchers.IO) {
        try {
            (context as AndroidContext).androidContext.contentResolver.openInputStream(
                Uri.parse(Res.getUri(url))
            )
        } catch (e: Exception) {
            logger.error { "Failed loading asset $url: $e" }
            null
        }
    }
}