package com.comet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

class SpriteBank(private val context: Context, private val displaySizePx: Int) {

    private val rightBank = mutableMapOf<String, Bitmap>()
    private val leftBank = mutableMapOf<String, Bitmap>()

    private val spriteResMap = mapOf(
        "idle_1" to R.drawable.idle_1,
        "idle_blink" to R.drawable.idle_blink,
        "walk_1" to R.drawable.walk_1,
        "walk_2" to R.drawable.walk_2,
        "walk_3" to R.drawable.walk_3,
        "grabbed" to R.drawable.grabbed,
        "thrown" to R.drawable.thrown,
        "happy_pet" to R.drawable.happy_pet,
        "sleep_1" to R.drawable.sleep_1,
        "sleep_2" to R.drawable.sleep_2,
        "eat_1" to R.drawable.eat_1,
        "eat_2" to R.drawable.eat_2,
        "react_link" to R.drawable.react_link,
        "react_number" to R.drawable.react_number,
        "react_code" to R.drawable.react_code,
        "react_longtext" to R.drawable.react_longtext
    )

    init {
        loadAll()
    }

    private fun loadAll() {
        val flipMatrix = Matrix().apply { postScale(-1f, 1f) }

        for ((name, resId) in spriteResMap) {
            val original = BitmapFactory.decodeResource(context.resources, resId) ?: continue
            val scaled = Bitmap.createScaledBitmap(original, displaySizePx, displaySizePx, true)
            rightBank[name] = scaled

            val flipped = Bitmap.createBitmap(
                scaled, 0, 0, scaled.width, scaled.height, flipMatrix, true
            )
            leftBank[name] = flipped
        }
    }

    fun get(pose: String, facingRight: Boolean = true): Bitmap {
        val bank = if (facingRight) rightBank else leftBank
        return bank[pose] ?: bank["idle_1"] ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    fun getFrame(state: PetState, stateTime: Float, facingRight: Boolean = true): Bitmap {
        val pose = when (state) {
            PetState.IDLE -> {
                if ((stateTime % 3.6f) < 0.18f) "idle_blink" else "idle_1"
            }
            PetState.WALK -> {
                val frame = ((stateTime * 6f).toInt() % 3) + 1
                "walk_$frame"
            }
            PetState.GRABBED -> "grabbed"
            PetState.THROWN -> "thrown"
            PetState.HAPPY_PET -> "happy_pet"
            PetState.EAT -> {
                val frame = ((stateTime * 4.5f).toInt() % 2) + 1
                "eat_$frame"
            }
            PetState.SLEEP -> {
                val frame = ((stateTime * 0.8f).toInt() % 2) + 1
                "sleep_$frame"
            }
            PetState.REACT_LINK -> "react_link"
            PetState.REACT_NUMBER -> "react_number"
            PetState.REACT_CODE -> "react_code"
            PetState.REACT_LONGTEXT -> "react_longtext"
        }
        return get(pose, facingRight)
    }
}
