package com.example.customcontroller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.customcontroller.network.ControllerState
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class ControllerView(context: Context) : View(context) {

    companion object {
        private const val PREFS = "controller_layout"
        private const val LAYOUT_VERSION = 1
        private const val DEADZONE = 0.10f

        private const val CROSS = 1 shl 0
        private const val CIRCLE = 1 shl 1
        private const val SQUARE = 1 shl 2
        private const val TRIANGLE = 1 shl 3
        private const val L1 = 1 shl 4
        private const val R1 = 1 shl 5
        private const val SELECT = 1 shl 6
        private const val START = 1 shl 7
        private const val PS = 1 shl 0

        private const val L2_CODE = 1000
        private const val R2_CODE = 1001
        private const val UP = 1002
        private const val DOWN = 1003
        private const val LEFT = 1004
        private const val RIGHT = 1005
        private const val PS_CODE = 2000

        private enum class Kind { RECT, CIRCLE, STICK }

        private data class LayoutItem(
            val id: String,
            val label: String,
            val code: Int,
            val kind: Kind,
            var x: Float,
            var y: Float,
            var w: Float,
            var h: Float,
            var defaultX: Float = x,
            var defaultY: Float = y,
            var defaultW: Float = w,
            var defaultH: Float = h
        )
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val state = ControllerState()
    private val items = ArrayList<LayoutItem>()
    private val buttonRects = ArrayList<Pair<RectF, LayoutItem>>()
    private val buttonPointers = HashMap<Int, LayoutItem>()

    private var editMode = false
    private var editPointerId = -1
    private var editItem: LayoutItem? = null
    private var editOffsetX = 0f
    private var editOffsetY = 0f
    private var toolbarAction = ""

    private val leftStickId = "left_stick"
    private val rightStickId = "right_stick"

    var onStateChanged: ((Int, Int, Int, Int, Int, Int, Int, Int) -> Unit)? = null

    init {
        isFocusable = true
        createDefaultLayout()
        loadLayout()
    }

    private fun createDefaultLayout() {
        items.clear()
        fun add(
            id: String,
            label: String,
            code: Int,
            kind: Kind,
            x: Float,
            y: Float,
            w: Float,
            h: Float
        ) {
            items.add(LayoutItem(id, label, code, kind, x, y, w, h, x, y, w, h))
        }

        add("l1", "L1", L1, Kind.RECT, .185f, .125f, .17f, .13f)
        add("r1", "R1", R1, Kind.RECT, .815f, .125f, .17f, .13f)
        add("l2", "L2", L2_CODE, Kind.RECT, .185f, .27f, .17f, .14f)
        add("r2", "R2", R2_CODE, Kind.RECT, .815f, .27f, .17f, .14f)

        val s = .09f
        val dpadCx = .20f
        val dpadCy = .55f
        add("dpad_up", "↑", UP, Kind.RECT, dpadCx, dpadCy - 1.175f * s, 2f * s, 1.65f * s)
        add("dpad_down", "↓", DOWN, Kind.RECT, dpadCx, dpadCy + 1.175f * s, 2f * s, 1.65f * s)
        add("dpad_left", "←", LEFT, Kind.RECT, dpadCx - 1.175f * s, dpadCy, 1.65f * s, 2f * s)
        add("dpad_right", "→", RIGHT, Kind.RECT, dpadCx + 1.175f * s, dpadCy, 1.65f * s, 2f * s)

        val r = .065f
        val faceCx = .80f
        val faceCy = .55f
        add("triangle", "△", TRIANGLE, Kind.CIRCLE, faceCx, faceCy - 1.9f * r, 2f * r, 2f * r)
        add("square", "□", SQUARE, Kind.CIRCLE, faceCx - 1.9f * r, faceCy, 2f * r, 2f * r)
        add("circle", "○", CIRCLE, Kind.CIRCLE, faceCx + 1.9f * r, faceCy, 2f * r, 2f * r)
        add("cross", "×", CROSS, Kind.CIRCLE, faceCx, faceCy + 1.9f * r, 2f * r, 2f * r)

        add("left_stick", "", 0, Kind.STICK, .39f, .68f, .15f, .15f)
        add("right_stick", "", 0, Kind.STICK, .61f, .68f, .15f, .15f)

        add("select", "SELECT", SELECT, Kind.RECT, .455f, .50f, .05f, .08f)
        add("start", "START", START, Kind.RECT, .545f, .50f, .05f, .08f)
        add("ps", "PS", PS_CODE, Kind.RECT, .50f, .385f, .05f, .09f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF111318.toInt())

        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = 0xFF242830.toInt()
        canvas.drawRoundRect(0f, 0f, w, h, 32f, 32f, paint)

        if (editMode) {
            drawEditorBackground(canvas, w, h)
        }

        drawItems(canvas)

        if (editMode) {
            drawEditorToolbar(canvas, w, h)
        } else {
            drawEditorButton(canvas, w, h)
        }
    }

    private fun drawItems(canvas: Canvas) {
        buttonRects.clear()
        for (item in items) {
            val rect = itemRect(item)
            if (item.kind == Kind.STICK) {
                drawStickItem(canvas, item, rect)
            } else {
                addButton(canvas, rect, item)
            }
        }
    }

    private fun drawEditorBackground(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x445C6675
        val grid = min(w, h) / 10f
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, paint)
            x += grid
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, paint)
            y += grid
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawStickItem(canvas: Canvas, item: LayoutItem, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val outerR = min(rect.width(), rect.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.color = if (editMode) 0xFF515966.toInt() else 0xFF3A3F48.toInt()
        canvas.drawCircle(cx, cy, outerR, paint)
        paint.color = 0xFF858C99.toInt()
        canvas.drawCircle(cx, cy, outerR * .74f, paint)
        if (editMode) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xFFCBD2DC.toInt()
            canvas.drawCircle(cx, cy, outerR, paint)
            paint.style = Paint.Style.FILL
        }
    }


    private fun drawEditorButton(canvas: Canvas, w: Float, h: Float) {
        val rect = editorButtonRect()
        paint.style = Paint.Style.FILL
        paint.color = 0xFF50555D.toInt()
        canvas.drawRoundRect(rect, 14f, 14f, paint)
        paint.color = 0xFFE8EAF0.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(rect.height(), rect.width()) * .25f
        canvas.drawText("EDIT LAYOUT", rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun drawEditorToolbar(canvas: Canvas, w: Float, h: Float) {
        val y = h * .015f
        val bh = min(h * .055f, 72f)
        val gap = 14f
        val widths = floatArrayOf(w * .19f, w * .15f, w * .15f)
        val labels = arrayOf("SAVE & EXIT", "RESET", "EXIT")
        val ids = arrayOf("save", "reset", "exit")
        var x = (w - (widths.sum() + gap * 2f)) / 2f
        for (i in labels.indices) {
            val rect = RectF(x, y, x + widths[i], y + bh)
            paint.color = when (ids[i]) {
                "save" -> 0xFF3D7C52.toInt()
                "reset" -> 0xFF6E5B37.toInt()
                else -> 0xFF50545B.toInt()
            }
            canvas.drawRoundRect(rect, 14f, 14f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = bh * .29f
            canvas.drawText(labels[i], rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
            x += widths[i] + gap
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(h, w) * .022f
        paint.color = 0xDDE8EDF4.toInt()
        canvas.drawText("Drag any control to reposition it. Layout is saved on SAVE & EXIT.", w / 2f, h * .095f, paint)
    }

    private fun addButton(canvas: Canvas, rect: RectF, item: LayoutItem) {
        buttonRects.add(rect to item)
        paint.style = Paint.Style.FILL
        paint.color = if (editMode && editItem === item) 0xFF516C8A.toInt() else 0xFF343A44.toInt()
        if (item.kind == Kind.CIRCLE) {
            canvas.drawCircle(rect.centerX(), rect.centerY(), min(rect.width(), rect.height()) / 2f, paint)
        } else {
            canvas.drawRoundRect(rect, 18f, 18f, paint)
        }
        if (editMode) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0x667C8896
            if (item.kind == Kind.CIRCLE) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), min(rect.width(), rect.height()) / 2f, paint)
            } else {
                canvas.drawRoundRect(rect, 18f, 18f, paint)
            }
            paint.style = Paint.Style.FILL
        }

        if (item.label.isNotEmpty()) {
            paint.color = 0xFFE8EAF0.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = min(rect.width(), rect.height()) * if (item.label.length > 2) .28f else .72f
            canvas.drawText(item.label, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return handleEditTouch(event)
        return handleControllerTouch(event)
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                toolbarAction = editorToolbarHit(x, y)
                if (toolbarAction.isNotEmpty()) {
                    when (toolbarAction) {
                        "save" -> exitEditor(save = true)
                        "reset" -> resetLayout()
                        "exit" -> exitEditor(save = false)
                    }
                    return true
                }

                val hit = items.asReversed().firstOrNull { item -> itemRect(item).contains(x, y) }
                if (hit != null) {
                    editPointerId = event.getPointerId(0)
                    editItem = hit
                    val rect = itemRect(hit)
                    editOffsetX = x - rect.centerX()
                    editOffsetY = y - rect.centerY()
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (editPointerId == -1 || editItem == null) return true
                val index = event.findPointerIndex(editPointerId)
                if (index < 0) return true
                val item = editItem ?: return true
                val halfW = item.w * width / 2f
                val halfH = item.h * height / 2f
                item.x = ((event.getX(index) - editOffsetX) / width.toFloat()).coerceIn(halfW / width, 1f - halfW / width)
                item.y = ((event.getY(index) - editOffsetY) / height.toFloat()).coerceIn(halfH / height, 1f - halfH / height)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                editPointerId = -1
                editItem = null
                toolbarAction = ""
                invalidate()
            }
        }
        return true
    }

    private fun handleControllerTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)

                if (tryStartStick(id, x, y, leftStickId) || tryStartStick(id, x, y, rightStickId)) {
                    emit()
                    invalidate()
                    return true
                }

                val item = findButton(x, y)
                if (item != null) {
                    if (item.id == "editor") {
                        return true
                    }
                    buttonPointers[id] = item
                    setItemPressed(item, true, x, y)
                    emit()
                    invalidate()
                } else if (editorButtonRect().contains(x, y)) {
                    enterEditor()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    when {
                        id == pointerIdFor(leftStickId) -> updateStick(leftStickId, x, y)
                        id == pointerIdFor(rightStickId) -> updateStick(rightStickId, x, y)
                        buttonPointers.containsKey(id) -> setItemPressed(buttonPointers[id]!!, true, x, y)
                    }
                }
                emit()
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.actionIndex
                val id = event.getPointerId(i)
                if (pointerIdFor(leftStickId) == id) releaseStick(leftStickId)
                if (pointerIdFor(rightStickId) == id) releaseStick(rightStickId)
                buttonPointers.remove(id)?.let { setItemPressed(it, false, event.getX(i), event.getY(i)) }
                emit()
                invalidate()
            }
        }
        return true
    }

    private fun editorToolbarHit(x: Float, y: Float): String {
        val w = width.toFloat()
        val h = height.toFloat()
        val bh = min(h * .055f, 72f)
        val gap = 14f
        val widths = floatArrayOf(w * .19f, w * .15f, w * .15f)
        var left = (w - (widths.sum() + gap * 2f)) / 2f
        val names = arrayOf("save", "reset", "exit")
        for (i in names.indices) {
            val rect = RectF(left, h * .015f, left + widths[i], h * .015f + bh)
            if (rect.contains(x, y)) return names[i]
            left += widths[i] + gap
        }
        return ""
    }

    private fun editorButtonRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * .425f, h * .015f, w * .575f, h * .08f)
    }

    private fun enterEditor() {
        resetInputState()
        editMode = true
        invalidate()
    }

    private fun exitEditor(save: Boolean) {
        if (save) saveLayout()
        editMode = false
        editPointerId = -1
        editItem = null
        resetInputState()
        invalidate()
    }

    private fun resetLayout() {
        for (item in items) {
            item.x = item.defaultX
            item.y = item.defaultY
            item.w = item.defaultW
            item.h = item.defaultH
        }
        saveLayout()
        invalidate()
    }

    private fun saveLayout() {
        val e = prefs.edit().putInt("version", LAYOUT_VERSION)
        for (item in items) {
            e.putFloat("${item.id}_x", item.x)
                .putFloat("${item.id}_y", item.y)
                .putFloat("${item.id}_w", item.w)
                .putFloat("${item.id}_h", item.h)
        }
        e.apply()
    }

    private fun loadLayout() {
        if (prefs.getInt("version", -1) != LAYOUT_VERSION) return
        for (item in items) {
            item.x = prefs.getFloat("${item.id}_x", item.x)
            item.y = prefs.getFloat("${item.id}_y", item.y)
            item.w = prefs.getFloat("${item.id}_w", item.w)
            item.h = prefs.getFloat("${item.id}_h", item.h)
        }
    }

    private fun resetInputState() {
        buttonPointers.clear()
        items.firstOrNull { it.id == leftStickId }?.let { }
        state.digital = 0
        state.extra = 0
        state.leftX = 0
        state.leftY = 0
        state.rightX = 0
        state.rightY = 0
        state.leftTrigger = 0
        state.rightTrigger = 0
        stickPointerIds[leftStickId] = -1
        stickPointerIds[rightStickId] = -1
        emit()
    }

    private val stickPointerIds = HashMap<String, Int>().apply {
        put(leftStickId, -1)
        put(rightStickId, -1)
    }

    private fun pointerIdFor(id: String): Int = stickPointerIds[id] ?: -1

    private fun tryStartStick(id: Int, x: Float, y: Float, stickId: String): Boolean {
        if (pointerIdFor(stickId) != -1) return false
        val item = items.firstOrNull { it.id == stickId } ?: return false
        val rect = itemRect(item)
        val radius = min(rect.width(), rect.height()) * .95f
        if (hypot(x - rect.centerX(), y - rect.centerY()) <= radius) {
            stickPointerIds[stickId] = id
            updateStick(stickId, x, y)
            return true
        }
        return false
    }

    private fun updateStick(stickId: String, x: Float, y: Float) {
        val item = items.firstOrNull { it.id == stickId } ?: return
        val rect = itemRect(item)
        val radius = min(rect.width(), rect.height()) * .74f
        var dx = (x - rect.centerX()) / radius
        var dy = (y - rect.centerY()) / radius
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len > 1f) {
            dx /= len
            dy /= len
        }
        if (stickId == leftStickId) {
            state.leftX = (applyDeadzone(dx) * 32767f).roundToInt()
            state.leftY = (-applyDeadzone(dy) * 32767f).roundToInt()
        } else {
            state.rightX = (applyDeadzone(dx) * 32767f).roundToInt()
            state.rightY = (-applyDeadzone(dy) * 32767f).roundToInt()
        }
    }

    private fun releaseStick(stickId: String) {
        stickPointerIds[stickId] = -1
        if (stickId == leftStickId) {
            state.leftX = 0
            state.leftY = 0
        } else {
            state.rightX = 0
            state.rightY = 0
        }
    }

    private fun findButton(x: Float, y: Float): LayoutItem? {
        if (editorButtonRect().contains(x, y)) return null
        return buttonRects.asReversed().firstOrNull { it.first.contains(x, y) }?.second
    }

    private fun setItemPressed(item: LayoutItem, pressed: Boolean, x: Float, y: Float) {
        if (item.code == L2_CODE || item.code == R2_CODE) {
            val rect = itemRect(item)
            val value = if (pressed) {
                val ratio = 1f - ((y - rect.top) / rect.height())
                (ratio.coerceIn(0f, 1f) * 255f).roundToInt()
            } else 0
            if (item.code == L2_CODE) state.leftTrigger = value else state.rightTrigger = value
            return
        }

        if (item.code == PS_CODE) {
            state.extra = if (pressed) state.extra or PS else state.extra and PS.inv()
            return
        }

        val dpadBit = when (item.code) {
            UP -> 1 shl 1
            DOWN -> 1 shl 2
            LEFT -> 1 shl 3
            RIGHT -> 1 shl 4
            else -> 0
        }
        if (dpadBit != 0) {
            state.extra = if (pressed) state.extra or dpadBit else state.extra and dpadBit.inv()
            return
        }

        state.digital = if (pressed) state.digital or item.code else state.digital and item.code.inv()
    }

    private fun itemRect(item: LayoutItem): RectF {
        return RectF(
            item.x * width - item.w * width / 2f,
            item.y * height - item.h * height / 2f,
            item.x * width + item.w * width / 2f,
            item.y * height + item.h * height / 2f
        )
    }

    private fun applyDeadzone(v: Float): Float {
        val sign = if (v < 0f) -1f else 1f
        val a = kotlin.math.abs(v)
        if (a <= DEADZONE) return 0f
        return sign * ((a - DEADZONE) / (1f - DEADZONE)).coerceIn(0f, 1f)
    }

    private fun emit() {
        onStateChanged?.invoke(
            state.digital,
            state.extra,
            state.leftX,
            state.leftY,
            state.rightX,
            state.rightY,
            state.leftTrigger,
            state.rightTrigger
        )
    }
}
