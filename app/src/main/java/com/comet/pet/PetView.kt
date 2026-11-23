package com.comet.pet

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@SuppressLint("ViewConstructor")
class PetView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val spriteBank: SpriteBank
) : View(context) {

    interface Listener {
        fun onDismissRequested()
        fun onFeedRequested()
        fun onSleepToggleRequested()
    }

    var listener: Listener? = null

    // Screen dimensions
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var floorY = 2000

    // Pet size (pixels)
    var petSizePx = (96 * resources.displayMetrics.density).toInt()
        set(value) {
            field = value
            bubbleWidth = (value * 1.8f).toInt()
            bubbleHeight = (value * 0.7f).toInt()
            requestLayout()
        }

    // Positions & Physics
    var posX = 200f
    var posY = 1500f
    private var velX = 0f
    private var velY = 0f
    private val gravity = 1400f // px/s^2
    private val bounceDamping = 0.45f
    private val friction = 0.98f

    // Pet State
    var currentState = PetState.IDLE
        private set
    private var facingLeft = false
    private var stateTime = 0f
    private var animFrame = 0

    // Speech bubble
    private var speechText: String? = null
    private var speechAlpha = 0f
    private var speechTimer = 0f
    private var bubbleWidth = (petSizePx * 1.8f).toInt()
    private var bubbleHeight = (petSizePx * 0.7f).toInt()

    // Autonomous AI timers
    private var idleDuration = 3f
    private var walkDuration = 4f
    private var walkSpeed = 90f // px/s
    private var timeSinceLastTouch = 0f
    private val sleepThreshold = 35f // Sleep after 35s of inactivity

    // Particles
    private val particleSystem = ParticleSystem()

    // Paints
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 25, 27, 34)
        style = Paint.Style.FILL
    }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 120, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12.5f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // Touch interaction
    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var velocityTracker: VelocityTracker? = null

    // Loop
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = System.nanoTime()
    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = ((now - lastFrameTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
            lastFrameTime = now

            updatePhysicsAndAI(dt)
            invalidate()

            handler.postDelayed(this, 16) // ~60 fps
        }
    }

    init {
        updateScreenMetrics()
        say("Привет! Я Комета!", 3.5f)
    }

    fun updateScreenMetrics() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        floorY = screenHeight - (petSizePx * 1.5f).toInt()
    }

    fun start() {
        lastFrameTime = System.nanoTime()
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
    }

    fun stop() {
        handler.removeCallbacks(frameRunnable)
    }

    fun say(text: String, duration: Float = 4.0f) {
        speechText = text
        speechTimer = duration
        speechAlpha = 1.0f
        invalidate()
    }

    fun feed() {
        setState(PetState.EAT)
        particleSystem.emitSnack(posX + petSizePx * 0.5f, posY + petSizePx * 0.5f)
        val phrases = listOf("Ням-ням!", "Обожаю печеньки!", "Вкуснотища!", "Ещё одну, пожалуйста!")
        say(phrases.random(), 3f)
    }

    fun toggleSleep() {
        if (currentState == PetState.SLEEP) {
            wakeUp()
        } else {
            putToSleep()
        }
    }

    fun putToSleep() {
        setState(PetState.SLEEP)
        say("Хр-р-р... Спокойной ночи! zZz", 3.5f)
    }

    fun wakeUp() {
        setState(PetState.IDLE)
        say("Доброе утро! Я выспалась!", 3f)
    }

    fun reactClipboard(reaction: ClipboardReaction) {
        setState(reaction.state)
        say(reaction.phrase, 4.5f)
    }

    private fun setState(newState: PetState) {
        currentState = newState
        stateTime = 0f
        animFrame = 0
        if (newState != PetState.SLEEP && newState != PetState.GRABBED && newState != PetState.THROWN) {
            velX = 0f
            velY = 0f
        }
    }

    private fun updatePhysicsAndAI(dt: Float) {
        stateTime += dt
        timeSinceLastTouch += dt
        particleSystem.update(dt)

        // Speech bubble fade
        if (speechTimer > 0) {
            speechTimer -= dt
            if (speechTimer <= 0.5f) {
                speechAlpha = (speechTimer / 0.5f).coerceIn(0f, 1f)
            }
        } else {
            speechAlpha = 0f
            speechText = null
        }

        // Particle generation
        if (currentState == PetState.SLEEP && stateTime % 1.2f < dt) {
            particleSystem.emitZzz(posX + (if (facingLeft) petSizePx * 0.3f else petSizePx * 0.7f), posY + petSizePx * 0.3f)
        }

        // Behavior State Machine
        when (currentState) {
            PetState.GRABBED -> {
                // Position is updated in onTouchEvent
            }

            PetState.THROWN -> {
                // Ballistic physics
                velY += gravity * dt
                posX += velX * dt
                posY += velY * dt

                // Wall collisions
                if (posX < 0) {
                    posX = 0f
                    velX = -velX * bounceDamping
                } else if (posX > screenWidth - petSizePx) {
                    posX = (screenWidth - petSizePx).toFloat()
                    velX = -velX * bounceDamping
                }

                // Floor collision
                if (posY >= floorY) {
                    posY = floorY.toFloat()
                    velY = -velY * bounceDamping
                    velX *= friction

                    if (abs(velY) < 60f && abs(velX) < 40f) {
                        velY = 0f
                        velX = 0f
                        setState(PetState.IDLE)
                    }
                }
            }

            PetState.IDLE -> {
                if (timeSinceLastTouch > sleepThreshold) {
                    putToSleep()
                    return
                }

                if (stateTime > idleDuration) {
                    // Pick next action: walk or stay idle
                    if (Random.nextFloat() < 0.65f) {
                        setState(PetState.WALK)
                        facingLeft = Random.nextBoolean()
                        walkDuration = Random.nextFloat() * 3f + 2f
                    } else {
                        stateTime = 0f
                        idleDuration = Random.nextFloat() * 3f + 1.5f
                    }
                }
            }

            PetState.WALK -> {
                val speed = if (facingLeft) -walkSpeed else walkSpeed
                posX += speed * dt

                // Reverse at edges
                if (posX <= 10f) {
                    posX = 10f
                    facingLeft = false
                } else if (posX >= screenWidth - petSizePx - 10f) {
                    posX = (screenWidth - petSizePx - 10f).toFloat()
                    facingLeft = true
                }

                if (stateTime > walkDuration) {
                    setState(PetState.IDLE)
                    idleDuration = Random.nextFloat() * 4f + 2f
                }
            }

            PetState.EAT -> {
                if (stateTime > 2.5f) {
                    setState(PetState.IDLE)
                }
            }

            PetState.HAPPY_PET -> {
                if (stateTime > 2.2f) {
                    setState(PetState.IDLE)
                }
            }

            PetState.SLEEP -> {
                // Snoozing peacefully
            }

            PetState.REACT_LINK, PetState.REACT_NUMBER, PetState.REACT_CODE, PetState.REACT_LONGTEXT -> {
                if (stateTime > 3.8f) {
                    setState(PetState.IDLE)
                }
            }
        }

        // Clamp inside screen bounds
        posX = posX.coerceIn(0f, (screenWidth - petSizePx).toFloat())
        posY = posY.coerceIn(0f, floorY.toFloat())

        // Sync overlay layout position
        syncWindowPosition()
    }

    private fun syncWindowPosition() {
        val targetX = posX.toInt() - (bubbleWidth / 4)
        val targetY = (posY - (bubbleHeight * 1.1f)).toInt()

        if (layoutParams.x != targetX || layoutParams.y != targetY) {
            layoutParams.x = targetX
            layoutParams.y = targetY
            try {
                windowManager.updateViewLayout(this, layoutParams)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        timeSinceLastTouch = 0f

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                val distTotal = kotlin.math.hypot(event.rawX - touchDownX, event.rawY - touchDownY)

                if (!isDragging && distTotal > 15f) {
                    isDragging = true
                    setState(PetState.GRABBED)
                }

                if (isDragging) {
                    posX += dx
                    posY += dy
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f

                    if (abs(vx) > 300f || abs(vy) > 300f) {
                        velX = vx.coerceIn(-2500f, 2500f)
                        velY = vy.coerceIn(-2500f, 2500f)
                        setState(PetState.THROWN)
                        facingLeft = velX < 0
                        val phrases = listOf("Уи-и-и-и!", "Полетели!", "Держи меня!", "Я комета!")
                        say(phrases.random(), 2f)
                    } else {
                        setState(PetState.IDLE)
                    }
                } else {
                    // Tap detection!
                    onSingleTap()
                }

                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                if (currentState == PetState.GRABBED) {
                    setState(PetState.IDLE)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private var lastTapTime = 0L
    private fun onSingleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 350) {
            // Double tap -> Feed!
            feed()
            lastTapTime = 0
            return
        }
        lastTapTime = now

        if (currentState == PetState.SLEEP) {
            wakeUp()
            return
        }

        // Petting
        setState(PetState.HAPPY_PET)
        particleSystem.emitHeart(posX + petSizePx * 0.5f, posY + petSizePx * 0.3f)
        val petPhrases = listOf(
            "Мурр-мяу! Тепло!",
            "Ты самый лучший хозяин!",
            "Обожаю, когда чешут за ушком!",
            "Фыр-фыр! Спасибо за ласку!",
            "Мур-р-р... счастье!"
        )
        say(petPhrases.random(), 3f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalW = petSizePx + bubbleWidth
        val totalH = petSizePx + bubbleHeight + (20 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(totalW, totalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val petDrawX = (bubbleWidth / 4f)
        val petDrawY = bubbleHeight.toFloat()

        // 1. Draw contact shadow under the pet
        val shadowW = petSizePx * 0.7f
        val shadowH = petSizePx * 0.16f
        val shadowRect = RectF(
            petDrawX + (petSizePx - shadowW) / 2f,
            petDrawY + petSizePx - shadowH * 0.7f,
            petDrawX + (petSizePx + shadowW) / 2f,
            petDrawY + petSizePx + shadowH * 0.3f
        )
        canvas.drawOval(shadowRect, shadowPaint)

        // 2. Pick current sprite bitmap
        val bmp = spriteBank.getFrame(currentState, stateTime)
        if (bmp != null) {
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = RectF(petDrawX, petDrawY, petDrawX + petSizePx, petDrawY + petSizePx)

            if (facingLeft) {
                canvas.save()
                canvas.scale(-1f, 1f, dst.centerX(), dst.centerY())
                canvas.drawBitmap(bmp, src, dst, spritePaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bmp, src, dst, spritePaint)
            }
        }

        // 3. Draw particles
        particleSystem.draw(canvas, petDrawX - (posX - (bubbleWidth / 4f)), petDrawY - (posY - bubbleHeight))

        // 4. Draw Speech Bubble if active
        val text = speechText
        if (text != null && speechAlpha > 0.05f) {
            drawSpeechBubble(canvas, text, petDrawX + petSizePx * 0.5f, petDrawY)
        }
    }

    private fun drawSpeechBubble(canvas: Canvas, text: String, anchorX: Float, anchorY: Float) {
        val originalAlpha = (speechAlpha * 255).toInt()
        bubbleBgPaint.alpha = (speechAlpha * 235).toInt()
        bubbleStrokePaint.alpha = (speechAlpha * 140).toInt()
        textPaint.alpha = originalAlpha

        val padding = 16f * resources.displayMetrics.density
        val maxWidth = (bubbleWidth - padding * 2).coerceAtLeast(100f).toInt()

        @Suppress("DEPRECATION")
        val staticLayout = StaticLayout(
            text,
            textPaint,
            maxWidth,
            Layout.Alignment.ALIGN_CENTER,
            1.1f,
            0.0f,
            false
        )

        val bubbleW = staticLayout.width + padding * 2
        val bubbleH = staticLayout.height + padding * 1.5f

        val left = anchorX - (bubbleW / 2f)
        val top = anchorY - bubbleH - (12f * resources.displayMetrics.density)
        val right = left + bubbleW
        val bottom = top + bubbleH

        val rect = RectF(left, top, right, bottom)
        val radius = 16f * resources.displayMetrics.density

        // Bubble rounded background
        canvas.drawRoundRect(rect, radius, radius, bubbleBgPaint)
        canvas.drawRoundRect(rect, radius, radius, bubbleStrokePaint)

        // Pointer triangle tail
        val tailPath = Path().apply {
            moveTo(anchorX - 12f, bottom - 1f)
            lineTo(anchorX, bottom + 12f)
            lineTo(anchorX + 12f, bottom - 1f)
            close()
        }
        canvas.drawPath(tailPath, bubbleBgPaint)

        // Text
        canvas.save()
        canvas.translate(left + padding, top + (padding * 0.75f))
        staticLayout.draw(canvas)
        canvas.restore()
    }
}
