package com.comet.pet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var age: Float,
    val life: Float,
    val kind: String, // "heart", "crumb", "zzz"
    val color: Int,
    val size: Float,
    val text: String = "",
    val gravity: Float = 0f,
    val swayAmp: Float = 0f,
    val swayFreq: Float = 0f,
    val phase: Float = 0f
)

class ParticleSystem {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun update(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += dt
            if (p.age >= p.life) {
                iterator.remove()
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.swayAmp > 0f) {
                p.x += sin(p.age * p.swayFreq + p.phase) * p.swayAmp * dt
            }
        }
    }

    fun emitZzz(x: Float, y: Float) {
        val glyph = if (Random.nextBoolean()) "z" else "Z"
        particles.add(
            Particle(
                x = x + Random.nextFloat() * 16f - 8f,
                y = y + Random.nextFloat() * 8f - 4f,
                vx = 20f + Random.nextFloat() * 15f,
                vy = -40f - Random.nextFloat() * 20f,
                age = 0f,
                life = 2.0f,
                kind = "zzz",
                color = Color.parseColor("#9BB8FF"),
                size = 28f + Random.nextFloat() * 10f,
                text = glyph,
                swayAmp = 16f,
                swayFreq = 3f,
                phase = Random.nextFloat() * 6.28f
            )
        )
    }

    fun emitHeart(x: Float, y: Float, count: Int = 4) {
        val colors = intArrayOf(
            Color.parseColor("#FF6B8B"),
            Color.parseColor("#FF8FA3"),
            Color.parseColor("#FFA726")
        )
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = x + Random.nextFloat() * 30f - 15f,
                    y = y + Random.nextFloat() * 16f - 8f,
                    vx = Random.nextFloat() * 60f - 30f,
                    vy = -70f - Random.nextFloat() * 50f,
                    age = 0f,
                    life = 1.2f,
                    kind = "heart",
                    color = colors[Random.nextInt(colors.size)],
                    size = 26f + Random.nextFloat() * 10f,
                    swayAmp = 20f,
                    swayFreq = 4f,
                    phase = Random.nextFloat() * 6.28f
                )
            )
        }
    }

    fun emitSnack(x: Float, y: Float, count: Int = 5) {
        val colors = intArrayOf(
            Color.parseColor("#F4B956"),
            Color.parseColor("#E29837"),
            Color.parseColor("#FFD88A")
        )
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = x + Random.nextFloat() * 20f - 10f,
                    y = y + Random.nextFloat() * 10f - 5f,
                    vx = Random.nextFloat() * 90f - 45f,
                    vy = -100f - Random.nextFloat() * 50f,
                    age = 0f,
                    life = 0.8f,
                    kind = "crumb",
                    color = colors[Random.nextInt(colors.size)],
                    size = 9f + Random.nextFloat() * 5f,
                    gravity = 500f
                )
            )
        }
    }

    fun draw(canvas: Canvas, offsetX: Float = 0f, offsetY: Float = 0f) {
        for (p in particles) {
            val progress = p.age / p.life
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            paint.color = p.color
            paint.alpha = alpha

            val drawX = p.x + offsetX
            val drawY = p.y + offsetY

            when (p.kind) {
                "zzz" -> {
                    paint.textSize = p.size
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(p.text, drawX, drawY, paint)
                }
                "heart" -> {
                    paint.textSize = p.size
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("♥", drawX, drawY, paint)
                }
                "crumb" -> {
                    canvas.drawCircle(drawX, drawY, p.size / 2f, paint)
                }
            }
        }
    }
}
