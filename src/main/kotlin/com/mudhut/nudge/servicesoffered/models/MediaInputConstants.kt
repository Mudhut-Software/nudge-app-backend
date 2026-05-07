package com.mudhut.nudge.servicesoffered.models

object MediaInputConstants {
    /**
     * Public-id prefix accepted by both the inbound DTO validator and the BE-side
     * Cloudinary destroy guard. Keep these two callers in sync via this constant —
     * defense in depth depends on it.
     */
    const val PUBLIC_ID_PATTERN = "^nudge/(images|videos)/.+"
}
