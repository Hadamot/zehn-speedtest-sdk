package tj.zehn.rmbt.ui.util

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import tj.zehn.rmbt.ui.R

/**
 * Small animations and haptics that make the screens feel responsive. All of them are skipped when
 * the user turned animations off in the system settings ("Remove animations" / animator scale 0).
 */
internal object UiFeedback {

    private const val PRESS_SCALE = 0.94f
    private const val PRESS_DURATION_MS = 120L
    private const val PULSE_SCALE = 1.06f
    private const val PULSE_DURATION_MS = 1_100L
    private const val ENTER_DURATION_MS = 350L
    private const val ENTER_STAGGER_MS = 60L
    private const val POP_SCALE = 1.15f
    private const val POP_DURATION_MS = 300L
    private const val BEACON_ALPHA = 0.35f
    private const val BEACON_DURATION_MS = 2_000L

    private val animationsEnabled: Boolean
        get() = (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

    /** Shrinks the view slightly while it is pressed. Does not consume the touch, so clicks keep working. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressScale(view: View) {
        view.setOnTouchListener { v, event ->
            if (animationsEnabled) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setStartDelay(0)
                            .setDuration(PRESS_DURATION_MS).setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(PRESS_DURATION_MS)
                            .setInterpolator(OvershootInterpolator()).start()
                }
            }
            false
        }
    }

    /** Short tap vibration, as for a keyboard key. Respects the system "touch feedback" setting. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Distinct vibration for a completed action (e.g. a finished test). */
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    /** Endless gentle "breathing" of the view, inviting the user to tap it. Stop it with [stopPulse]. */
    fun startPulse(view: View, scale: Float = PULSE_SCALE) {
        if (!animationsEnabled || view.getTag(TAG_PULSE) != null) return
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scale)
        ).apply {
            duration = PULSE_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        view.setTag(TAG_PULSE, animator)
    }

    fun stopPulse(view: View) {
        (view.getTag(TAG_PULSE) as? ValueAnimator)?.cancel()
        view.setTag(TAG_PULSE, null)
        view.scaleX = 1f
        view.scaleY = 1f
    }

    /**
     * Endless "beacon": the view grows from [fromScale] to [toScale] while fading out, then starts over,
     * like a ring of light spreading from a button. Give several rings different [startDelayMs] for a
     * continuous flow. The view should start with alpha 0. Stop it with [stopBeacon].
     */
    fun startBeacon(view: View, fromScale: Float, toScale: Float, startDelayMs: Long = 0) {
        if (!animationsEnabled || view.getTag(TAG_BEACON) != null) return
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, fromScale, toScale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, fromScale, toScale),
            PropertyValuesHolder.ofFloat(View.ALPHA, BEACON_ALPHA, 0f)
        ).apply {
            duration = BEACON_DURATION_MS
            startDelay = startDelayMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
        view.setTag(TAG_BEACON, animator)
    }

    fun stopBeacon(view: View) {
        (view.getTag(TAG_BEACON) as? ValueAnimator)?.cancel()
        view.setTag(TAG_BEACON, null)
        view.alpha = 0f
    }

    /** Fades the views in from slightly below, one after another. */
    fun enter(vararg views: View) = enterGroups(*views.map { listOf(it) }.toTypedArray())

    /** Like [enter], but the views of one group (e.g. a card and its texts) move together. */
    fun enterGroups(vararg groups: List<View>) {
        if (!animationsEnabled) return
        val offset = groups.firstOrNull()?.firstOrNull()?.resources?.displayMetrics?.density?.times(16f) ?: return
        groups.forEachIndexed { index, group ->
            group.forEach { view ->
                view.alpha = 0f
                view.translationY = offset
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * ENTER_STAGGER_MS)
                    .setDuration(ENTER_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    /** Briefly enlarges the view to draw attention to a value that has just been finalized. */
    fun pop(view: View) {
        if (!animationsEnabled) return
        view.animate().cancel()
        view.scaleX = POP_SCALE
        view.scaleY = POP_SCALE
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(0)
            .setDuration(POP_DURATION_MS)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private val TAG_PULSE = R.id.rmbt_tag_pulse_animator
    private val TAG_BEACON = R.id.rmbt_tag_beacon_animator
}
