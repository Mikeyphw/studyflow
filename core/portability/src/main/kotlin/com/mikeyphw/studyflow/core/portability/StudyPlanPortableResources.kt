package com.mikeyphw.studyflow.core.portability

object StudyPlanPortableResources {
    fun forManifest(manifest: StudyPlanPackageManifest): List<StudyPlanPortableResource> =
        manifest.annexPayloads.map { payload ->
            StudyPlanPortableResource(
                fileName = payload.fileName,
                mimeType = payload.mimeType,
                byteSize = payload.byteSize,
                checksumSha256 = payload.checksumSha256.lowercase(),
                handler = handlerForMimeType(payload.mimeType),
            )
        }

    fun handlerForMimeType(rawMimeType: String): PortableResourceHandler {
        val mimeType = rawMimeType.substringBefore(';').trim().lowercase()
        return when {
            mimeType == "application/pdf" -> PortableResourceHandler.DOCUMENT
            mimeType == "application/msword" -> PortableResourceHandler.DOCUMENT
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                PortableResourceHandler.DOCUMENT
            mimeType.startsWith("text/") -> PortableResourceHandler.TEXT
            mimeType.startsWith("image/") -> PortableResourceHandler.IMAGE
            mimeType.startsWith("audio/") -> PortableResourceHandler.AUDIO
            mimeType.startsWith("video/") -> PortableResourceHandler.VIDEO
            else -> PortableResourceHandler.GENERIC
        }
    }
}
