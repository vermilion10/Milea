package com.github.vermilion10.milea.ui.screens.poster

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.github.vermilion10.milea.data.model.DistanceUnit
import com.github.vermilion10.milea.data.model.Trip
import com.github.vermilion10.milea.data.model.averageSpeedMps
import com.github.vermilion10.milea.data.model.TripCategory
import com.github.vermilion10.milea.data.model.TripPoint
import com.github.vermilion10.milea.util.Units
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

enum class PosterStyle(val label: String) {
    MIDNIGHT("Midnight"),
    EMBER("Ember"),
    PAPER("Paper"),
    STICKER("Sticker")
}

enum class PosterFormat(val label: String, val width: Int, val height: Int) {
    STORY("Story", 1080, 1920),
    PORTRAIT("Portrait", 1080, 1350),
    SQUARE("Square", 1080, 1080)
}

private class Palette(
    val backgroundTop: Int?,
    val backgroundBottom: Int?,
    val glow: Int?,
    val routeStart: Int,
    val routeEnd: Int,
    val text: Int,
    val subtle: Int,
    val shadow: Boolean
)

private fun PosterStyle.palette() = when (this) {
    PosterStyle.MIDNIGHT -> Palette(
        backgroundTop = 0xFF0B2328.toInt(), backgroundBottom = 0xFF03080A.toInt(),
        glow = 0x5534D1C6, routeStart = 0xFF7CF3E8.toInt(), routeEnd = 0xFF4FA3FF.toInt(),
        text = Color.WHITE, subtle = 0x99FFFFFF.toInt(), shadow = false
    )
    PosterStyle.EMBER -> Palette(
        backgroundTop = 0xFF2A1208.toInt(), backgroundBottom = 0xFF0D0604.toInt(),
        glow = 0x55FF6A1F, routeStart = 0xFFFFC15E.toInt(), routeEnd = 0xFFFF5A1F.toInt(),
        text = Color.WHITE, subtle = 0x99FFFFFF.toInt(), shadow = false
    )
    PosterStyle.PAPER -> Palette(
        backgroundTop = 0xFFF6F3EC.toInt(), backgroundBottom = 0xFFECE7DC.toInt(),
        glow = null, routeStart = 0xFF14302F.toInt(), routeEnd = 0xFF00696E.toInt(),
        text = 0xFF14302F.toInt(), subtle = 0x9914302F.toInt(), shadow = false
    )
    PosterStyle.STICKER -> Palette(
        backgroundTop = null, backgroundBottom = null,
        glow = null, routeStart = Color.WHITE, routeEnd = Color.WHITE,
        text = Color.WHITE, subtle = 0xDDFFFFFF.toInt(), shadow = true
    )
}

data class PosterContent(
    val trip: Trip,
    val points: List<TripPoint>,
    val unit: DistanceUnit,
    val vehicleName: String?
)

/** Strava-like title from the time of day and category, e.g. "Evening commute". */
fun posterTitle(trip: Trip): String {
    val hour = Calendar.getInstance().apply { timeInMillis = trip.startTime }.get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    val what = when (trip.category) {
        TripCategory.COMMUTE -> "commute"
        TripCategory.BUSINESS -> "business trip"
        TripCategory.LEISURE -> "drive"
        TripCategory.OTHER -> "drive"
    }
    return "$part $what"
}

object TripPosterRenderer {

    fun render(content: PosterContent, style: PosterStyle, format: PosterFormat): Bitmap {
        val w = format.width.toFloat()
        val h = format.height.toFloat()
        val bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = style.palette()
        val margin = w * 0.08f

        drawBackground(canvas, w, h, p)

        // Header: wordmark and date.
        var y = margin + 20f
        val brand = textPaint(p, 30f, Typeface.create("sans-serif-black", Typeface.NORMAL)).apply {
            letterSpacing = 0.35f
        }
        canvas.drawText("MILEA", margin, y, brand)
        val dateText = SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(content.trip.startTime))
        val datePaint = textPaint(p, 30f, Typeface.create("sans-serif", Typeface.NORMAL), subtle = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateText, w - margin, y, datePaint)

        y += 96f
        val title = textPaint(p, if (format == PosterFormat.SQUARE) 64f else 76f, Typeface.create("sans-serif-medium", Typeface.BOLD))
        canvas.drawText(posterTitle(content.trip), margin, y, title)

        // Stats block height is fixed; the route takes whatever is left.
        val statsHeight = if (format == PosterFormat.SQUARE) 350f else 380f
        val footerHeight = 70f
        val routeTop = y + 48f
        val routeBottom = h - margin - footerHeight - statsHeight
        val routeBox = RectF(margin, routeTop, w - margin, routeBottom)

        if (content.points.size >= 2) {
            drawRoute(canvas, routeBox, content.points, p)
        } else {
            drawNoRoute(canvas, routeBox, p)
        }

        drawStats(canvas, content, p, margin, routeBottom + 24f, w - margin * 2, format)

        // Footer: vehicle.
        content.vehicleName?.let {
            val foot = textPaint(p, 30f, Typeface.create("sans-serif-medium", Typeface.NORMAL), subtle = true)
            canvas.drawText(it, margin, h - margin, foot)
        }
        val tag = textPaint(p, 26f, Typeface.create("sans-serif", Typeface.NORMAL), subtle = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("tracked with Milea", w - margin, h - margin, tag)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float, p: Palette) {
        val top = p.backgroundTop ?: return
        val bottom = p.backgroundBottom ?: top
        canvas.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        })
        p.glow?.let { glow ->
            canvas.drawCircle(w * 0.8f, h * 0.3f, w * 0.8f, Paint().apply {
                shader = RadialGradient(w * 0.8f, h * 0.3f, w * 0.8f, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            })
        }
    }

    private fun textPaint(p: Palette, size: Float, typeface: Typeface, subtle: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (subtle) p.subtle else p.text
            textSize = size
            this.typeface = typeface
            if (p.shadow) setShadowLayer(8f, 0f, 2f, 0x66000000)
        }

    private fun drawRoute(canvas: Canvas, box: RectF, points: List<TripPoint>, p: Palette) {
        val sampled = if (points.size > 1500) points.filterIndexed { i, _ -> i % (points.size / 1500 + 1) == 0 } + points.last() else points
        // Equirectangular projection, scaled so a degree of longitude is as
        // wide as it really is at this latitude.
        val midLat = (sampled.maxOf { it.latitude } + sampled.minOf { it.latitude }) / 2
        val lonScale = cos(Math.toRadians(midLat))
        val xs = sampled.map { it.longitude * lonScale }
        val ys = sampled.map { -it.latitude }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = max(maxX - minX, 1e-6)
        val spanY = max(maxY - minY, 1e-6)
        val inset = 40f
        val scale = min((box.width() - inset * 2) / spanX, (box.height() - inset * 2) / spanY)
        val offX = box.left + (box.width() - spanX * scale) / 2
        val offY = box.top + (box.height() - spanY * scale) / 2
        fun px(i: Int) = (offX + (xs[i] - minX) * scale).toFloat()
        fun py(i: Int) = (offY + (ys[i] - minY) * scale).toFloat()

        val path = Path().apply {
            moveTo(px(0), py(0))
            for (i in 1 until sampled.size) lineTo(px(i), py(i))
        }
        val gradient = LinearGradient(px(0), py(0), px(sampled.lastIndex), py(sampled.lastIndex), p.routeStart, p.routeEnd, Shader.TileMode.CLAMP)

        if (p.glow != null) {
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 28f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = gradient
                alpha = 110
                maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
            })
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = gradient
            if (p.shadow) setShadowLayer(10f, 0f, 3f, 0x66000000)
        })

        // Start: hollow ring. End: filled dot.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f; color = p.routeStart
        }
        canvas.drawCircle(px(0), py(0), 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.backgroundBottom ?: 0x66000000
        })
        canvas.drawCircle(px(0), py(0), 16f, ring)
        canvas.drawCircle(px(sampled.lastIndex), py(sampled.lastIndex), 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.routeEnd
            if (p.shadow) setShadowLayer(8f, 0f, 2f, 0x66000000)
        })
    }

    /** Manual trips have no GPS route; draw a stylised road instead. */
    private fun drawNoRoute(canvas: Canvas, box: RectF, p: Palette) {
        val path = Path().apply {
            moveTo(box.left + box.width() * 0.1f, box.bottom - box.height() * 0.15f)
            cubicTo(
                box.left + box.width() * 0.45f, box.bottom - box.height() * 0.1f,
                box.left + box.width() * 0.2f, box.top + box.height() * 0.35f,
                box.right - box.width() * 0.1f, box.top + box.height() * 0.2f
            )
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            color = p.routeEnd
            alpha = 90
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(36f, 28f), 0f)
        })
    }

    private fun drawStats(
        canvas: Canvas,
        content: PosterContent,
        p: Palette,
        left: Float,
        top: Float,
        width: Float,
        format: PosterFormat
    ) {
        val trip = content.trip
        val unit = content.unit
        val label = textPaint(p, 30f, Typeface.create("sans-serif-medium", Typeface.NORMAL), subtle = true).apply {
            letterSpacing = 0.12f
        }
        val big = textPaint(p, if (format == PosterFormat.SQUARE) 128f else 150f, Typeface.create("sans-serif-condensed", Typeface.BOLD))
        val unitPaint = textPaint(p, 52f, Typeface.create("sans-serif-condensed", Typeface.BOLD))
        val value = textPaint(p, 60f, Typeface.create("sans-serif-condensed", Typeface.BOLD))

        var y = top + 30f
        canvas.drawText("DISTANCE", left, y, label)
        y += big.textSize * 0.95f
        val distance = String.format(Locale.getDefault(), "%.1f", Units.distance(trip.distance, unit))
        canvas.drawText(distance, left, y, big)
        canvas.drawText(" " + Units.distanceLabel(unit), left + big.measureText(distance), y, unitPaint)

        y += 90f
        val columns = listOf(
            "TIME" to formatElapsed(trip.duration),
            "AVG SPEED" to Units.formatSpeed(trip.averageSpeedMps * 3.6f, unit),
            "MAX SPEED" to if (trip.maxSpeed > 0f) Units.formatSpeed(trip.maxSpeed * 3.6f, unit) else "--"
        )
        val colWidth = width / columns.size
        columns.forEachIndexed { i, (l, v) ->
            val x = left + colWidth * i
            canvas.drawText(l, x, y, label)
            canvas.drawText(v, x, y + 66f, value)
        }
    }

    private fun formatElapsed(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}
