package com.example.customcontroller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.customcontroller.network.ControllerState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ControllerView(context: Context) : View(context) {

    companion object {
        private const val PREFS = "controller_layout"
        private const val LAYOUT_VERSION = 2

        private const val DEADZONE = 0.05f

        private const val CROSS = 1 shl 0
        private const val CIRCLE = 1 shl 1
        private const val SQUARE = 1 shl 2
        private const val TRIANGLE = 1 shl 3
        private const val L1 = 1 shl 4
        private const val R1 = 1 shl 5
        private const val SELECT = 1 shl 6
        private const val START = 1 shl 7

        private const val PS = 1 shl 0
        private const val UP = 1 shl 1
        private const val DOWN = 1 shl 2
        private const val LEFT = 1 shl 3
        private const val RIGHT = 1 shl 4

        private const val L2_CODE = 1000
        private const val R2_CODE = 1001
        private const val UP_CODE = 1002
        private const val DOWN_CODE = 1003
        private const val LEFT_CODE = 1004
        private const val RIGHT_CODE = 1005
        private const val PS_CODE = 2000

        private const val MIN_RECT_SIZE = 0.035f
        private const val MIN_STICK_SIZE = 0.08f
        private const val MAX_SIZE = 0.45f

        // Resize handle is intentionally generous for touchscreens.
        private const val RESIZE_HANDLE_RATIO = 0.28f
        private const val RESIZE_TOUCH_EXTRA_DP = 12f

        private enum class Kind {
            RECT,
            CIRCLE,
            STICK
        }

        private enum class EditOperation {
            NONE,
            MOVE,
            RESIZE
        }

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

    private val prefs =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val state =
        ControllerState()

    private val items =
        ArrayList<LayoutItem>()

    private val buttonRects =
        ArrayList<Pair<RectF, LayoutItem>>()

    private val buttonPointers =
        HashMap<Int, LayoutItem>()

    private val buttonPressCounts =
        HashMap<String, Int>()

    private val pointerPositions =
        HashMap<Int, Pair<Float, Float>>()

    // Starting Y position for L2/R2 drag-based analog control.
    // A trigger press always starts at full value (255), then moving
    // the finger downward reduces the value. This also makes L2+R2
    // reliable under multi-touch.
    private val triggerStartY =
        HashMap<Int, Float>()

    private val leftStickId =
        "left_stick"

    private val rightStickId =
        "right_stick"

    private val stickPointerIds =
        HashMap<String, Int>().apply {
            put(leftStickId, -1)
            put(rightStickId, -1)
        }

    private var editMode = false

    private var editPointerId = -1
    private var editItem: LayoutItem? = null
    private var editOffsetX = 0f
    private var editOffsetY = 0f

    private var editOperation =
        EditOperation.NONE

    // Used when resizing.
    private var resizeStartPointerX = 0f
    private var resizeStartPointerY = 0f
    private var resizeStartW = 0f
    private var resizeStartH = 0f
    private var resizeStartX = 0f
    private var resizeStartY = 0f

    private var toolbarAction = ""

    private val resizeHandlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    var onStateChanged:
        ((Int, Int, Int, Int, Int, Int, Int, Int) -> Unit)? =
        null

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
            items.add(
                LayoutItem(
                    id = id,
                    label = label,
                    code = code,
                    kind = kind,
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    defaultX = x,
                    defaultY = y,
                    defaultW = w,
                    defaultH = h
                )
            )
        }

        add(
            "l1",
            "L1",
            L1,
            Kind.RECT,
            0.185f,
            0.125f,
            0.17f,
            0.13f
        )

        add(
            "r1",
            "R1",
            R1,
            Kind.RECT,
            0.815f,
            0.125f,
            0.17f,
            0.13f
        )

        add(
            "l2",
            "L2",
            L2_CODE,
            Kind.RECT,
            0.185f,
            0.27f,
            0.17f,
            0.14f
        )

        add(
            "r2",
            "R2",
            R2_CODE,
            Kind.RECT,
            0.815f,
            0.27f,
            0.17f,
            0.14f
        )

        val dpadSize = 0.09f
        val dpadCx = 0.20f
        val dpadCy = 0.55f

        add(
            "dpad_up",
            "↑",
            UP_CODE,
            Kind.RECT,
            dpadCx,
            dpadCy - 1.175f * dpadSize,
            2f * dpadSize,
            1.65f * dpadSize
        )

        add(
            "dpad_down",
            "↓",
            DOWN_CODE,
            Kind.RECT,
            dpadCx,
            dpadCy + 1.175f * dpadSize,
            2f * dpadSize,
            1.65f * dpadSize
        )

        add(
            "dpad_left",
            "←",
            LEFT_CODE,
            Kind.RECT,
            dpadCx - 1.175f * dpadSize,
            dpadCy,
            1.65f * dpadSize,
            2f * dpadSize
        )

        add(
            "dpad_right",
            "→",
            RIGHT_CODE,
            Kind.RECT,
            dpadCx + 1.175f * dpadSize,
            dpadCy,
            1.65f * dpadSize,
            2f * dpadSize
        )

        val faceRadius = 0.065f
        val faceCx = 0.80f
        val faceCy = 0.55f

        add(
            "triangle",
            "△",
            TRIANGLE,
            Kind.CIRCLE,
            faceCx,
            faceCy - 1.9f * faceRadius,
            2f * faceRadius,
            2f * faceRadius
        )

        add(
            "square",
            "□",
            SQUARE,
            Kind.CIRCLE,
            faceCx - 1.9f * faceRadius,
            faceCy,
            2f * faceRadius,
            2f * faceRadius
        )

        add(
            "circle",
            "○",
            CIRCLE,
            Kind.CIRCLE,
            faceCx + 1.9f * faceRadius,
            faceCy,
            2f * faceRadius,
            2f * faceRadius
        )

        add(
            "cross",
            "×",
            CROSS,
            Kind.CIRCLE,
            faceCx,
            faceCy + 1.9f * faceRadius,
            2f * faceRadius,
            2f * faceRadius
        )

        add(
            leftStickId,
            "",
            0,
            Kind.STICK,
            0.39f,
            0.68f,
            0.15f,
            0.15f
        )

        add(
            rightStickId,
            "",
            0,
            Kind.STICK,
            0.61f,
            0.68f,
            0.15f,
            0.15f
        )

        add(
            "select",
            "SELECT",
            SELECT,
            Kind.RECT,
            0.455f,
            0.50f,
            0.05f,
            0.08f
        )

        add(
            "start",
            "START",
            START,
            Kind.RECT,
            0.545f,
            0.50f,
            0.05f,
            0.08f
        )

        add(
            "ps",
            "PS",
            PS_CODE,
            Kind.RECT,
            0.50f,
            0.385f,
            0.05f,
            0.09f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(0xFF111318.toInt())

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        paint.style =
            Paint.Style.FILL

        paint.color =
            0xFF242830.toInt()

        canvas.drawRoundRect(
            0f,
            0f,
            w,
            h,
            32f,
            32f,
            paint
        )

        if (editMode) {
            drawEditorBackground(
                canvas,
                w,
                h
            )
        }

        drawItems(canvas)

        if (editMode) {
            drawEditorToolbar(
                canvas,
                w,
                h
            )
        } else {
            drawEditorButton(
                canvas,
                w,
                h
            )
        }
    }

    private fun drawItems(
        canvas: Canvas
    ) {
        buttonRects.clear()

        for (item in items) {
            val rect =
                itemRect(item)

            when (item.kind) {
                Kind.STICK ->
                    drawStickItem(
                        canvas,
                        item,
                        rect
                    )

                Kind.RECT,
                Kind.CIRCLE ->
                    addButton(
                        canvas,
                        rect,
                        item
                    )
            }

            if (
                editMode &&
                editItem?.id == item.id
            ) {
                drawResizeHandle(
                    canvas,
                    rect,
                    item
                )
            }
        }
    }

    private fun drawEditorBackground(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth = 2f
        paint.color =
            0x445C6675

        val grid =
            min(w, h) / 10f

        var x = 0f

        while (x <= w) {
            canvas.drawLine(
                x,
                0f,
                x,
                h,
                paint
            )

            x += grid
        }

        var y = 0f

        while (y <= h) {
            canvas.drawLine(
                0f,
                y,
                w,
                y,
                paint
            )

            y += grid
        }

        paint.style =
            Paint.Style.FILL
    }

    private fun drawResizeHandle(
        canvas: Canvas,
        rect: RectF,
        item: LayoutItem
    ) {
        val size =
            max(
                dp(18f),
                min(
                    rect.width(),
                    rect.height()
                ) * RESIZE_HANDLE_RATIO
            )

        val cx =
            rect.right -
                size * 0.58f

        val cy =
            rect.bottom -
                size * 0.58f

        resizeHandlePaint.style =
            Paint.Style.FILL

        resizeHandlePaint.color =
            if (
                editOperation ==
                EditOperation.RESIZE
            ) {
                0xFFE4B64A.toInt()
            } else {
                0xFFBFC7D2.toInt()
            }

        canvas.drawCircle(
            cx,
            cy,
            size * 0.32f,
            resizeHandlePaint
        )

        resizeHandlePaint.style =
            Paint.Style.STROKE

        resizeHandlePaint.strokeWidth =
            max(
                2f,
                dp(2f)
            )

        resizeHandlePaint.color =
            0xFF20252D.toInt()

        canvas.drawLine(
            cx - size * 0.20f,
            cy + size * 0.20f,
            cx + size * 0.20f,
            cy - size * 0.20f,
            resizeHandlePaint
        )

        resizeHandlePaint.style =
            Paint.Style.FILL
    }

    private fun drawStickItem(
        canvas: Canvas,
        item: LayoutItem,
        rect: RectF
    ) {
        val cx =
            rect.centerX()

        val cy =
            rect.centerY()

        val outerR =
            min(
                rect.width(),
                rect.height()
            ) / 2f

        paint.style =
            Paint.Style.FILL

        paint.color =
            if (editMode) {
                0xFF515966.toInt()
            } else {
                0xFF3A3F48.toInt()
            }

        canvas.drawCircle(
            cx,
            cy,
            outerR,
            paint
        )

        var knobX =
            cx

        var knobY =
            cy

        if (!editMode) {
            val xNorm =
                if (
                    item.id ==
                    leftStickId
                ) {
                    state.leftX /
                        32767f
                } else {
                    state.rightX /
                        32767f
                }

            val yNorm =
                if (
                    item.id ==
                    leftStickId
                ) {
                    -(state.leftY /
                        32767f)
                } else {
                    -(state.rightY /
                        32767f)
                }

            val knobTravel =
                outerR * 0.26f

            knobX =
                cx +
                    xNorm.coerceIn(
                        -1f,
                        1f
                    ) *
                    knobTravel

            knobY =
                cy +
                    yNorm.coerceIn(
                        -1f,
                        1f
                    ) *
                    knobTravel
        }

        paint.color =
            0xFF858C99.toInt()

        canvas.drawCircle(
            knobX,
            knobY,
            outerR * 0.74f,
            paint
        )

        if (!editMode) {
            paint.color =
                0xFFB7BEC9.toInt()

            canvas.drawCircle(
                knobX,
                knobY,
                outerR * 0.20f,
                paint
            )
        }

        if (editMode) {
            paint.style =
                Paint.Style.STROKE

            paint.strokeWidth = 2f

            paint.color =
                0xFFCBD2DC.toInt()

            canvas.drawCircle(
                cx,
                cy,
                outerR,
                paint
            )

            paint.style =
                Paint.Style.FILL
        }
    }

    private fun drawEditorButton(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val rect =
            editorButtonRect()

        paint.style =
            Paint.Style.FILL

        paint.color =
            0xFF50555D.toInt()

        canvas.drawRoundRect(
            rect,
            14f,
            14f,
            paint
        )

        paint.color =
            0xFFE8EAF0.toInt()

        paint.textAlign =
            Paint.Align.CENTER

        paint.textSize =
            min(
                rect.height(),
                rect.width()
            ) * 0.25f

        canvas.drawText(
            "EDIT LAYOUT",
            rect.centerX(),
            rect.centerY() -
                (
                    paint.ascent() +
                        paint.descent()
                    ) / 2f,
            paint
        )
    }

    private fun drawEditorToolbar(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val y =
            h * 0.015f

        val bh =
            min(
                h * 0.055f,
                72f
            )

        val gap =
            14f

        val widths =
            floatArrayOf(
                w * 0.19f,
                w * 0.15f,
                w * 0.15f
            )

        val labels =
            arrayOf(
                "SAVE & EXIT",
                "RESET",
                "EXIT"
            )

        val ids =
            arrayOf(
                "save",
                "reset",
                "exit"
            )

        var x =
            (
                w -
                    (
                        widths.sum() +
                            gap * 2f
                        )
                ) / 2f

        for (i in labels.indices) {
            val rect =
                RectF(
                    x,
                    y,
                    x + widths[i],
                    y + bh
                )

            paint.color =
                when (ids[i]) {
                    "save" ->
                        0xFF3D7C52.toInt()

                    "reset" ->
                        0xFF6E5B37.toInt()

                    else ->
                        0xFF50545B.toInt()
                }

            canvas.drawRoundRect(
                rect,
                14f,
                14f,
                paint
            )

            paint.color =
                0xFFFFFFFF.toInt()

            paint.textAlign =
                Paint.Align.CENTER

            paint.textSize =
                bh * 0.29f

            canvas.drawText(
                labels[i],
                rect.centerX(),
                rect.centerY() -
                    (
                        paint.ascent() +
                            paint.descent()
                        ) / 2f,
                paint
            )

            x +=
                widths[i] +
                    gap
        }

        paint.textAlign =
            Paint.Align.CENTER

        paint.textSize =
            min(h, w) * 0.022f

        paint.color =
            0xDDE8EDF4.toInt()

        canvas.drawText(
            "Drag to move. Drag the corner handle to resize. SAVE & EXIT to keep changes.",
            w / 2f,
            h * 0.095f,
            paint
        )
    }

    private fun addButton(
        canvas: Canvas,
        rect: RectF,
        item: LayoutItem
    ) {
        buttonRects.add(
            rect to item
        )

        val isPressed =
            !editMode &&
                (
                    buttonPressCounts[
                        item.id
                    ] ?: 0
                ) > 0

        paint.style =
            Paint.Style.FILL

        paint.color =
            when {
                editMode &&
                    editItem?.id ==
                    item.id ->
                    0xFF516C8A.toInt()

                isPressed ->
                    0xFF5A789F.toInt()

                else ->
                    0xFF343A44.toInt()
            }

        if (
            item.kind ==
            Kind.CIRCLE
        ) {
            canvas.drawCircle(
                rect.centerX(),
                rect.centerY(),
                min(
                    rect.width(),
                    rect.height()
                ) / 2f,
                paint
            )
        } else {
            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )
        }

        if (editMode) {
            paint.style =
                Paint.Style.STROKE

            paint.strokeWidth = 3f

            paint.color =
                0x667C8896

            if (
                item.kind ==
                Kind.CIRCLE
            ) {
                canvas.drawCircle(
                    rect.centerX(),
                    rect.centerY(),
                    min(
                        rect.width(),
                        rect.height()
                    ) / 2f,
                    paint
                )
            } else {
                canvas.drawRoundRect(
                    rect,
                    18f,
                    18f,
                    paint
                )
            }

            paint.style =
                Paint.Style.FILL
        }

        if (
            item.label.isNotEmpty()
        ) {
            paint.color =
                if (isPressed) {
                    0xFFFFFFFF.toInt()
                } else {
                    0xFFE8EAF0.toInt()
                }

            paint.textAlign =
                Paint.Align.CENTER

            paint.textSize =
                min(
                    rect.width(),
                    rect.height()
                ) *
                    if (
                        item.label.length > 2
                    ) {
                        0.28f
                    } else {
                        0.72f
                    }

            canvas.drawText(
                item.label,
                rect.centerX(),
                rect.centerY() -
                    (
                        paint.ascent() +
                            paint.descent()
                        ) / 2f,
                paint
            )
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        return if (editMode) {
            handleEditTouch(event)
        } else {
            handleControllerTouch(event)
        }
    }

    private fun handleEditTouch(
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val x =
                    event.x

                val y =
                    event.y

                toolbarAction =
                    editorToolbarHit(
                        x,
                        y
                    )

                if (
                    toolbarAction.isNotEmpty()
                ) {
                    when (toolbarAction) {
                        "save" ->
                            exitEditor(
                                save = true
                            )

                        "reset" ->
                            resetLayout()

                        "exit" ->
                            exitEditor(
                                save = false
                            )
                    }

                    return true
                }

                val hit =
                    items
                        .asReversed()
                        .firstOrNull { item ->
                            itemRect(item)
                                .contains(
                                    x,
                                    y
                                )
                        }

                if (
                    hit != null
                ) {
                    editPointerId =
                        event.getPointerId(0)

                    editItem =
                        hit

                    val rect =
                        itemRect(hit)

                    if (
                        isResizeHandleHit(
                            rect,
                            x,
                            y,
                            hit
                        )
                    ) {
                        editOperation =
                            EditOperation.RESIZE

                        resizeStartPointerX =
                            x

                        resizeStartPointerY =
                            y

                        resizeStartW =
                            hit.w

                        resizeStartH =
                            hit.h

                        resizeStartX =
                            hit.x

                        resizeStartY =
                            hit.y
                    } else {
                        editOperation =
                            EditOperation.MOVE

                        editOffsetX =
                            x -
                                rect.centerX()

                        editOffsetY =
                            y -
                                rect.centerY()
                    }

                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (
                    editPointerId ==
                    -1 ||
                    editItem == null
                ) {
                    return true
                }

                val index =
                    event.findPointerIndex(
                        editPointerId
                    )

                if (index < 0) {
                    return true
                }

                val item =
                    editItem
                        ?: return true

                val x =
                    event.getX(index)

                val y =
                    event.getY(index)

                when (
                    editOperation
                ) {
                    EditOperation.MOVE ->
                        moveEditItem(
                            item,
                            x,
                            y
                        )

                    EditOperation.RESIZE ->
                        resizeEditItem(
                            item,
                            x,
                            y
                        )

                    EditOperation.NONE ->
                        Unit
                }

                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId =
                    event.getPointerId(
                        event.actionIndex
                    )

                if (
                    pointerId ==
                    editPointerId
                ) {
                    clearEditInteraction()
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                clearEditInteraction()
                invalidate()
            }
        }

        return true
    }

    private fun moveEditItem(
        item: LayoutItem,
        pointerX: Float,
        pointerY: Float
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val halfW =
            item.w *
                width /
                2f

        val halfH =
            item.h *
                height /
                2f

        val x =
            pointerX -
                editOffsetX

        val y =
            pointerY -
                editOffsetY

        item.x =
            (
                x /
                    width.toFloat()
                ).coerceIn(
                    halfW /
                        width.toFloat(),
                    1f -
                        halfW /
                            width.toFloat()
                )

        item.y =
            (
                y /
                    height.toFloat()
                ).coerceIn(
                    halfH /
                        height.toFloat(),
                    1f -
                        halfH /
                            height.toFloat()
                )
    }

    private fun resizeEditItem(
        item: LayoutItem,
        pointerX: Float,
        pointerY: Float
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val dx =
            pointerX -
                resizeStartPointerX

        val dy =
            pointerY -
                resizeStartPointerY

        when (item.kind) {
            Kind.RECT -> {
                /*
                 * Rectangles may resize independently in X/Y.
                 * Center stays fixed.
                 */
                val newW =
                    resizeStartW +
                        (
                            dx * 2f /
                                width.toFloat()
                        )

                val newH =
                    resizeStartH +
                        (
                            dy * 2f /
                                height.toFloat()
                        )

                item.w =
                    newW.coerceIn(
                        MIN_RECT_SIZE,
                        MAX_SIZE
                    )

                item.h =
                    newH.coerceIn(
                        MIN_RECT_SIZE,
                        MAX_SIZE
                    )
            }

            Kind.CIRCLE,
            Kind.STICK -> {
                /*
                 * Circles and analogs MUST remain circular.
                 * Resize is based on radial distance from center.
                 */
                val centerX =
                    resizeStartX *
                        width.toFloat()

                val centerY =
                    resizeStartY *
                        height.toFloat()

                val radius =
                    hypot(
                        pointerX - centerX,
                        pointerY - centerY
                    )

                val base =
                    min(
                        width.toFloat(),
                        height.toFloat()
                    )

                val diameter =
                    (
                        radius * 2f
                    ) / base

                val minSize =
                    if (
                        item.kind ==
                        Kind.STICK
                    ) {
                        MIN_STICK_SIZE
                    } else {
                        MIN_RECT_SIZE
                    }

                val size =
                    diameter.coerceIn(
                        minSize,
                        MAX_SIZE
                    )

                item.w = size
                item.h = size
            }
        }

        /*
         * Keep the item completely inside the screen after resizing.
         */
        val halfW =
            item.w / 2f

        val halfH =
            item.h / 2f

        item.x =
            item.x.coerceIn(
                halfW,
                1f - halfW
            )

        item.y =
            item.y.coerceIn(
                halfH,
                1f - halfH
            )
    }

    private fun isResizeHandleHit(
        rect: RectF,
        x: Float,
        y: Float,
        item: LayoutItem
    ): Boolean {
        val minDimension =
            min(
                rect.width(),
                rect.height()
            )

        val handleSize =
            max(
                dp(26f),
                minDimension *
                    RESIZE_HANDLE_RATIO
            )

        val extra =
            dp(
                RESIZE_TOUCH_EXTRA_DP
            )

        val handleRect =
            RectF(
                rect.right -
                    handleSize -
                    extra,

                rect.bottom -
                    handleSize -
                    extra,

                rect.right +
                    extra,

                rect.bottom +
                    extra
            )

        return handleRect.contains(
            x,
            y
        )
    }

    private fun clearEditInteraction() {
        editPointerId = -1
        editItem = null
        editOperation =
            EditOperation.NONE
        toolbarAction = ""
    }

    private fun handleControllerTouch(
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index =
                    event.actionIndex

                val pointerId =
                    event.getPointerId(index)

                val x =
                    event.getX(index)

                val y =
                    event.getY(index)

                pointerPositions[pointerId] =
                    Pair(x, y)

                val item =
                    findButton(
                        x,
                        y
                    )

                if (item != null && isTrigger(item)) {
                    buttonPointers[pointerId] = item
                    triggerStartY[pointerId] = y
                    incrementButtonPress(
                        item,
                        x,
                        y
                    )
                    emit()
                    invalidate()
                    return true
                }

                if (
                    tryStartStick(
                        pointerId,
                        x,
                        y,
                        leftStickId
                    ) ||
                    tryStartStick(
                        pointerId,
                        x,
                        y,
                        rightStickId
                    )
                ) {
                    emit()
                    invalidate()
                    return true
                }

                val itemAfterStick =
                    findButton(
                        x,
                        y
                    )

                if (itemAfterStick != null) {
                    buttonPointers[pointerId] =
                        itemAfterStick

                    incrementButtonPress(
                        itemAfterStick,
                        x,
                        y
                    )

                    emit()
                    invalidate()

                    return true
                }

                if (
                    editorButtonRect()
                        .contains(
                            x,
                            y
                        )
                ) {
                    enterEditor()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (
                    i in
                    0 until event.pointerCount
                ) {
                    val pointerId =
                        event.getPointerId(i)

                    val x =
                        event.getX(i)

                    val y =
                        event.getY(i)

                    pointerPositions[pointerId] =
                        Pair(x, y)

                    when {
                        pointerId ==
                            pointerIdFor(
                                leftStickId
                            ) -> {
                            updateStick(
                                leftStickId,
                                x,
                                y
                            )
                        }

                        pointerId ==
                            pointerIdFor(
                                rightStickId
                            ) -> {
                            updateStick(
                                rightStickId,
                                x,
                                y
                            )
                        }

                        buttonPointers
                            .containsKey(
                                pointerId
                            ) -> {
                            val item =
                                buttonPointers[
                                    pointerId
                                ]
                                    ?: continue

                            if (isTrigger(item)) {
                                updateTriggerControl(
                                    pointerId,
                                    item,
                                    x,
                                    y
                                )
                            } else {
                                updatePressedControl(
                                    item,
                                    x,
                                    y
                                )
                            }
                        }
                    }
                }

                emit()
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index =
                    event.actionIndex

                val pointerId =
                    event.getPointerId(index)

                val x =
                    event.getX(index)

                val y =
                    event.getY(index)

                pointerPositions.remove(
                    pointerId
                )
                triggerStartY.remove(pointerId)

                releasePointer(
                    pointerId,
                    x,
                    y
                )

                emit()
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                val index =
                    event.actionIndex

                val pointerId =
                    event.getPointerId(index)

                val x =
                    event.getX(index)

                val y =
                    event.getY(index)

                pointerPositions.remove(
                    pointerId
                )
                triggerStartY.remove(pointerId)

                releasePointer(
                    pointerId,
                    x,
                    y
                )

                emit()
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                resetInputState()
                invalidate()
            }
        }

        return true
    }

    private fun releasePointer(
        pointerId: Int,
        x: Float,
        y: Float
    ) {
        if (
            pointerIdFor(
                leftStickId
            ) == pointerId
        ) {
            releaseStick(
                leftStickId
            )
        }

        if (
            pointerIdFor(
                rightStickId
            ) == pointerId
        ) {
            releaseStick(
                rightStickId
            )
        }

        buttonPointers
            .remove(pointerId)
            ?.let { item ->
                decrementButtonPress(
                    item,
                    x,
                    y
                )
            }
    }

    private fun editorToolbarHit(
        x: Float,
        y: Float
    ): String {
        val w =
            width.toFloat()

        val h =
            height.toFloat()

        val bh =
            min(
                h * 0.055f,
                72f
            )

        val gap = 14f

        val widths =
            floatArrayOf(
                w * 0.19f,
                w * 0.15f,
                w * 0.15f
            )

        val names =
            arrayOf(
                "save",
                "reset",
                "exit"
            )

        var left =
            (
                w -
                    (
                        widths.sum() +
                            gap * 2f
                        )
                ) / 2f

        for (
            i in names.indices
        ) {
            val rect =
                RectF(
                    left,
                    h * 0.015f,
                    left + widths[i],
                    h * 0.015f + bh
                )

            if (
                rect.contains(
                    x,
                    y
                )
            ) {
                return names[i]
            }

            left +=
                widths[i] +
                    gap
        }

        return ""
    }

    private fun editorButtonRect():
        RectF {
        val w =
            width.toFloat()

        val h =
            height.toFloat()

        return RectF(
            w * 0.425f,
            h * 0.015f,
            w * 0.575f,
            h * 0.08f
        )
    }

    private fun enterEditor() {
        resetInputState()

        editMode = true

        clearEditInteraction()

        invalidate()
    }

    private fun exitEditor(
        save: Boolean
    ) {
        if (save) {
            saveLayout()
        }

        editMode = false

        clearEditInteraction()

        resetInputState()

        invalidate()
    }

    private fun resetLayout() {
        for (item in items) {
            item.x =
                item.defaultX

            item.y =
                item.defaultY

            item.w =
                item.defaultW

            item.h =
                item.defaultH
        }

        saveLayout()

        invalidate()
    }

    private fun saveLayout() {
        val editor =
            prefs.edit()
                .putInt(
                    "version",
                    LAYOUT_VERSION
                )

        for (item in items) {
            editor
                .putFloat(
                    "${item.id}_x",
                    item.x
                )
                .putFloat(
                    "${item.id}_y",
                    item.y
                )
                .putFloat(
                    "${item.id}_w",
                    item.w
                )
                .putFloat(
                    "${item.id}_h",
                    item.h
                )
        }

        editor.apply()
    }

    private fun loadLayout() {
        if (
            prefs.getInt(
                "version",
                -1
            ) != LAYOUT_VERSION
        ) {
            return
        }

        for (item in items) {
            item.x =
                prefs.getFloat(
                    "${item.id}_x",
                    item.x
                )

            item.y =
                prefs.getFloat(
                    "${item.id}_y",
                    item.y
                )

            item.w =
                prefs.getFloat(
                    "${item.id}_w",
                    item.w
                )

            item.h =
                prefs.getFloat(
                    "${item.id}_h",
                    item.h
                )

            sanitizeLayoutItem(item)
        }
    }

    private fun sanitizeLayoutItem(
        item: LayoutItem
    ) {
        when (item.kind) {
            Kind.STICK,
            Kind.CIRCLE -> {
                val size =
                    (
                        (item.w + item.h) /
                            2f
                        ).coerceIn(
                            MIN_STICK_SIZE,
                            MAX_SIZE
                        )

                item.w = size
                item.h = size
            }

            Kind.RECT -> {
                item.w =
                    item.w.coerceIn(
                        MIN_RECT_SIZE,
                        MAX_SIZE
                    )

                item.h =
                    item.h.coerceIn(
                        MIN_RECT_SIZE,
                        MAX_SIZE
                    )
            }
        }

        item.x =
            item.x.coerceIn(
                item.w / 2f,
                1f -
                    item.w / 2f
            )

        item.y =
            item.y.coerceIn(
                item.h / 2f,
                1f -
                    item.h / 2f
            )
    }

    private fun resetInputState() {
        buttonPointers.clear()
        buttonPressCounts.clear()
        pointerPositions.clear()
        triggerStartY.clear()

        state.digital = 0
        state.extra = 0

        state.leftX = 0
        state.leftY = 0

        state.rightX = 0
        state.rightY = 0

        state.leftTrigger = 0
        state.rightTrigger = 0

        stickPointerIds[leftStickId] =
            -1

        stickPointerIds[rightStickId] =
            -1

        emit()
    }

    private fun pointerIdFor(
        stickId: String
    ): Int {
        return stickPointerIds[
            stickId
        ] ?: -1
    }

    private fun tryStartStick(
        pointerId: Int,
        x: Float,
        y: Float,
        stickId: String
    ): Boolean {
        if (
            pointerIdFor(
                stickId
            ) != -1
        ) {
            return false
        }

        val item =
            items.firstOrNull {
                it.id == stickId
            }
                ?: return false

        val rect =
            itemRect(item)

        val radius =
            min(
                rect.width(),
                rect.height()
            ) * 0.95f

        if (
            hypot(
                x - rect.centerX(),
                y - rect.centerY()
            ) <= radius
        ) {
            stickPointerIds[
                stickId
            ] = pointerId

            updateStick(
                stickId,
                x,
                y
            )

            return true
        }

        return false
    }

    private fun updateStick(
        stickId: String,
        x: Float,
        y: Float
    ) {
        val item =
            items.firstOrNull {
                it.id == stickId
            }
                ?: return

        val rect =
            itemRect(item)

        // The movement radius is based on half the visible stick size,
        // so the finger can reach the full -32768..32767 range without
        // having to travel outside the visual stick.
        val radius =
            min(
                rect.width(),
                rect.height()
            ) * 0.48f

        if (radius <= 0f) {
            return
        }

        var dx =
            (
                x -
                    rect.centerX()
                ) / radius

        var dy =
            (
                y -
                    rect.centerY()
                ) / radius

        // Clamp the touch position to a circular gate.
        val length =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            ).toFloat()

        if (length > 1f) {
            dx /= length
            dy /= length
        }

        // Radial deadzone instead of applying a separate deadzone
        // to X and Y. This keeps diagonal movement natural.
        val magnitude =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            ).toFloat()

        val filteredX: Float
        val filteredY: Float

        if (magnitude <= DEADZONE) {
            filteredX = 0f
            filteredY = 0f
        } else {
            val scaledMagnitude =
                (magnitude - DEADZONE) /
                    (1f - DEADZONE)

            val scale =
                scaledMagnitude / magnitude

            filteredX = dx * scale
            filteredY = dy * scale
        }

        if (
            stickId ==
            leftStickId
        ) {
            state.leftX =
                (
                    filteredX *
                        32767f
                    ).roundToInt()

            state.leftY =
                (
                    -filteredY *
                        32767f
                    ).roundToInt()
        } else {
            state.rightX =
                (
                    filteredX *
                        32767f
                    ).roundToInt()

            state.rightY =
                (
                    -filteredY *
                        32767f
                    ).roundToInt()
        }
    }

    private fun releaseStick(
        stickId: String
    ) {
        stickPointerIds[
            stickId
        ] = -1

        if (
            stickId ==
            leftStickId
        ) {
            state.leftX = 0
            state.leftY = 0
        } else {
            state.rightX = 0
            state.rightY = 0
        }
    }

    private fun findButton(
        x: Float,
        y: Float
    ): LayoutItem? {
        if (
            editorButtonRect()
                .contains(
                    x,
                    y
                )
        ) {
            return null
        }

        return buttonRects
            .asReversed()
            .firstOrNull {
                it.first.contains(
                    x,
                    y
                )
            }
            ?.second
    }

    private fun incrementButtonPress(
        item: LayoutItem,
        x: Float,
        y: Float
    ) {
        val oldCount =
            buttonPressCounts[
                item.id
            ] ?: 0

        buttonPressCounts[
            item.id
        ] =
            oldCount + 1

        if (oldCount == 0 && !editMode) {
            HapticUtils.click(this)
        }

        if (isTrigger(item)) {
            setItemPressed(
                item,
                true,
                x,
                y
            )
        } else {
            updatePressedControl(
                item,
                x,
                y
            )
        }
    }

    private fun decrementButtonPress(
        item: LayoutItem,
        x: Float,
        y: Float
    ) {
        val oldCount =
            buttonPressCounts[
                item.id
            ] ?: 0

        val newCount =
            (
                oldCount - 1
            ).coerceAtLeast(0)

        if (
            newCount == 0
        ) {
            buttonPressCounts.remove(
                item.id
            )
        } else {
            buttonPressCounts[
                item.id
            ] =
                newCount
        }

        if (
            newCount == 0
        ) {
            setItemPressed(
                item,
                false,
                x,
                y
            )
        } else if (
            item.code ==
                L2_CODE ||
            item.code ==
                R2_CODE
        ) {
            val remainingPointer =
                buttonPointers
                    .entries
                    .firstOrNull {
                        it.value.id ==
                            item.id
                    }
                    ?.key

            if (
                remainingPointer !=
                null
            ) {
                val position =
                    pointerPositions[
                        remainingPointer
                    ]

                if (
                    position !=
                    null
                ) {
                    updateTriggerControl(
                        remainingPointer,
                        item,
                        position.first,
                        position.second
                    )
                }
            }
        }
    }

    private fun isTrigger(
        item: LayoutItem
    ): Boolean {
        return item.code == L2_CODE ||
            item.code == R2_CODE
    }

    private fun updateTriggerControl(
        pointerId: Int,
        item: LayoutItem,
        x: Float,
        y: Float
    ) {
        val startY =
            triggerStartY[pointerId] ?: y

        val rect =
            itemRect(item)

        val travel =
            max(
                dp(24f),
                rect.height() * 0.90f
            )

        // Initial press is 255. Dragging downward reduces the value.
        val deltaY =
            (y - startY).coerceAtLeast(0f)

        val value =
            (255f * (1f - deltaY / travel))
                .roundToInt()
                .coerceIn(0, 255)

        if (item.code == L2_CODE) {
            state.leftTrigger = value
        } else {
            state.rightTrigger = value
        }
    }

    private fun updatePressedControl(
        item: LayoutItem,
        x: Float,
        y: Float
    ) {
        val active =
            (
                buttonPressCounts[
                    item.id
                ] ?: 0
            ) > 0

        if (!active) {
            return
        }

        setItemPressed(
            item,
            true,
            x,
            y
        )
    }

    private fun setItemPressed(
        item: LayoutItem,
        pressed: Boolean,
        x: Float,
        y: Float
    ) {
        if (isTrigger(item)) {
            val value =
                if (pressed) 255 else 0

            if (item.code == L2_CODE) {
                state.leftTrigger = value
            } else {
                state.rightTrigger = value
            }

            return
        }

        if (
            item.code ==
                PS_CODE
        ) {
            state.extra =
                if (pressed) {
                    state.extra or PS
                } else {
                    state.extra and
                        PS.inv()
                }

            return
        }

        val dpadBit =
            when (item.code) {
                UP_CODE -> UP
                DOWN_CODE -> DOWN
                LEFT_CODE -> LEFT
                RIGHT_CODE -> RIGHT
                else -> 0
            }

        if (
            dpadBit != 0
        ) {
            state.extra =
                if (pressed) {
                    state.extra or
                        dpadBit
                } else {
                    state.extra and
                        dpadBit.inv()
                }

            return
        }

        state.digital =
            if (pressed) {
                state.digital or
                    item.code
            } else {
                state.digital and
                    item.code.inv()
            }
    }

    private fun itemRect(
        item: LayoutItem
    ): RectF {
        val viewWidth =
            width.toFloat()

        val viewHeight =
            height.toFloat()

        return RectF(
            item.x *
                viewWidth -
                item.w *
                    viewWidth /
                    2f,

            item.y *
                viewHeight -
                item.h *
                    viewHeight /
                    2f,

            item.x *
                viewWidth +
                item.w *
                    viewWidth /
                    2f,

            item.y *
                viewHeight +
                item.h *
                    viewHeight /
                    2f
        )
    }

    private fun applyDeadzone(
    value: Float
): Float {
    val magnitude = abs(value)

    if (magnitude <= DEADZONE) {
        return 0f
    }

    val sign = if (value < 0f) -1f else 1f

    val normalized =
        (magnitude - DEADZONE) /
            (1f - DEADZONE)

    return sign *
        normalized.coerceIn(0f, 1f)
}

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources.displayMetrics.density
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