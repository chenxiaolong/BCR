/*
 * SPDX-FileCopyrightText: Google
 * SPDX-License-Identifier: Apache-2.0
 *
 * All icons here originated from Material Symbols: https://fonts.google.com/icons
 */

package com.chiller3.bcr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object Icons {
    object AutoMirrored {
        // https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/arrow_back.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
        val ArrowBack: ImageVector
            get() {
                if (_ArrowBack != null) {
                    return _ArrowBack!!
                }
                _ArrowBack =
                    ImageVector.Builder(
                        name = "arrow_back",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                        autoMirror = true,
                    )
                        .apply {
                            path(
                                fill = SolidColor(Color.Black),
                                fillAlpha = 1f,
                                stroke = null,
                                strokeAlpha = 1f,
                                strokeLineWidth = 1f,
                                strokeLineCap = StrokeCap.Butt,
                                strokeLineJoin = StrokeJoin.Bevel,
                                strokeLineMiter = 1f,
                                pathFillType = PathFillType.NonZero,
                            ) {
                                moveTo(7.83f, 13f)
                                lineToRelative(5.6f, 5.6f)
                                lineTo(12f, 20f)
                                lineTo(4f, 12f)
                                lineTo(12f, 4f)
                                lineToRelative(1.43f, 1.4f)
                                lineTo(7.83f, 11f)
                                horizontalLineTo(20f)
                                verticalLineToRelative(2f)
                                horizontalLineTo(7.83f)
                                close()
                            }
                        }
                        .build()
                return _ArrowBack!!
            }

        private var _ArrowBack: ImageVector? = null
    }

    // https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/check.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
    val Check: ImageVector
        get() {
            if (_Check != null) {
                return _Check!!
            }
            _Check =
                ImageVector.Builder(
                    name = "check",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                    .apply {
                        path(
                            fill = SolidColor(Color.Black),
                            fillAlpha = 1f,
                            stroke = null,
                            strokeAlpha = 1f,
                            strokeLineWidth = 1f,
                            strokeLineCap = StrokeCap.Butt,
                            strokeLineJoin = StrokeJoin.Bevel,
                            strokeLineMiter = 1f,
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(9.55f, 18f)
                            lineTo(3.85f, 12.3f)
                            lineTo(5.28f, 10.88f)
                            lineToRelative(4.28f, 4.28f)
                            lineTo(18.73f, 5.97f)
                            lineTo(20.15f, 7.4f)
                            lineTo(9.55f, 18f)
                            close()
                        }
                    }
                    .build()
            return _Check!!
        }

    private var _Check: ImageVector? = null

    // https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/close.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
    val Close: ImageVector
        get() {
            if (_Close != null) {
                return _Close!!
            }
            _Close =
                ImageVector.Builder(
                    name = "close",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                    .apply {
                        path(
                            fill = SolidColor(Color.Black),
                            fillAlpha = 1f,
                            stroke = null,
                            strokeAlpha = 1f,
                            strokeLineWidth = 1f,
                            strokeLineCap = StrokeCap.Butt,
                            strokeLineJoin = StrokeJoin.Bevel,
                            strokeLineMiter = 1f,
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(6.4f, 19f)
                            lineTo(5f, 17.6f)
                            lineTo(10.6f, 12f)
                            lineTo(5f, 6.4f)
                            lineTo(6.4f, 5f)
                            lineTo(12f, 10.6f)
                            lineTo(17.6f, 5f)
                            lineTo(19f, 6.4f)
                            lineTo(13.4f, 12f)
                            lineTo(19f, 17.6f)
                            lineTo(17.6f, 19f)
                            lineTo(12f, 13.4f)
                            lineTo(6.4f, 19f)
                            close()
                        }
                    }
                    .build()
            return _Close!!
        }

    private var _Close: ImageVector? = null

    // https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/more_vert.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
    val MoreVert: ImageVector
        get() {
            if (_MoreVert != null) {
                return _MoreVert!!
            }
            _MoreVert =
                ImageVector.Builder(
                    name = "more_vert",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                    .apply {
                        path(
                            fill = SolidColor(Color.Black),
                            fillAlpha = 1f,
                            stroke = null,
                            strokeAlpha = 1f,
                            strokeLineWidth = 1f,
                            strokeLineCap = StrokeCap.Butt,
                            strokeLineJoin = StrokeJoin.Bevel,
                            strokeLineMiter = 1f,
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(12f, 20f)
                            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                            reflectiveQuadTo(10f, 18f)
                            reflectiveQuadToRelative(0.59f, -1.41f)
                            reflectiveQuadTo(12f, 16f)
                            reflectiveQuadToRelative(1.41f, 0.59f)
                            quadTo(14f, 17.18f, 14f, 18f)
                            reflectiveQuadToRelative(-0.59f, 1.41f)
                            reflectiveQuadTo(12f, 20f)
                            close()
                            moveToRelative(0f, -6f)
                            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                            reflectiveQuadTo(10f, 12f)
                            reflectiveQuadToRelative(0.59f, -1.41f)
                            reflectiveQuadTo(12f, 10f)
                            reflectiveQuadToRelative(1.41f, 0.59f)
                            quadTo(14f, 11.18f, 14f, 12f)
                            reflectiveQuadToRelative(-0.59f, 1.41f)
                            reflectiveQuadTo(12f, 14f)
                            close()
                            moveTo(12f, 8f)
                            quadTo(11.18f, 8f, 10.59f, 7.41f)
                            reflectiveQuadTo(10f, 6f)
                            reflectiveQuadTo(10.59f, 4.59f)
                            reflectiveQuadTo(12f, 4f)
                            reflectiveQuadToRelative(1.41f, 0.59f)
                            quadTo(14f, 5.18f, 14f, 6f)
                            reflectiveQuadTo(13.41f, 7.41f)
                            reflectiveQuadTo(12f, 8f)
                            close()
                        }
                    }
                    .build()
            return _MoreVert!!
        }

    private var _MoreVert: ImageVector? = null
}
