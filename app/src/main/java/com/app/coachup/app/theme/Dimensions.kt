package com.app.coachup.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and corner-radius constants – mirrors iOS AppSpacing / AppRadius.
 *
 * iOS AppSpacing:
 *   xs = 4    sm = 8    md = 14    lg = 16    xl = 20    xxl = 24
 *
 * iOS AppRadius:
 *   pill = 100    card = 20    item = 14
 */
object Spacing {
    /** 4 dp – iOS xs */
    val xs: Dp = 4.dp

    /** 8 dp – iOS sm */
    val sm: Dp = 8.dp

    /** 14 dp – iOS md */
    val md: Dp = 14.dp

    /** 16 dp – iOS lg */
    val lg: Dp = 16.dp

    /** 20 dp – iOS xl */
    val xl: Dp = 20.dp

    /** 24 dp – iOS xxl */
    val xxl: Dp = 24.dp

    /** 32 dp – used in CameraPermissionView horizontal padding */
    val xxxl: Dp = 32.dp

    /** 40 dp – vertical padding in permission / camera views */
    val section: Dp = 40.dp
}

object Radius {
    /** 100 dp – pill / fully-rounded shape (iOS pill = 100) */
    val pill: Dp = 100.dp

    /** 20 dp – card corners (iOS card = 20) */
    val card: Dp = 20.dp

    /** 14 dp – list item corners (iOS item = 14) */
    val item: Dp = 14.dp

    /** 18 dp – message bubble corners */
    val bubble: Dp = 18.dp

    /** 20 dp – text field corners in MessageInputBar */
    val inputField: Dp = 20.dp

    /** 8 dp – banner / chip corners */
    val banner: Dp = 8.dp
}

object Elevation {
    /** Shadow equivalent of iOS: shadow(color: .black.opacity(0.05), radius: 35, y: 12) */
    val card: Dp = 4.dp

    /** Shadow for MessageInputBar: shadow(radius: 8, y: -2) */
    val inputBar: Dp = 4.dp
}
