package tj.zehn.rmbt.ui.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.databinding.RmbtActivityMeasurementBinding
import tj.zehn.rmbt.ui.model.MeasurementState
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.util.MeasurementPhases
import tj.zehn.rmbt.ui.util.UiFeedback
import tj.zehn.rmbt.ui.util.networkKindLabel
import tj.zehn.rmbt.ui.util.padForSystemBars
import tj.zehn.rmbt.ui.util.setDownload
import tj.zehn.rmbt.ui.util.setPing
import tj.zehn.rmbt.ui.util.setUpload

/**
 * The running test, copied from open-rmbt-android (MeasurementActivity): the S curve with the step
 * progress and the live speed, and the bottom panel with ping, download, upload and the speed chart.
 * Opens the result when the test finishes.
 */
internal class MeasurementActivity : RmbtActivity() {

    private val viewModel: MeasurementViewModel by viewModels { MeasurementViewModel.Factory }
    private lateinit var binding: RmbtActivityMeasurementBinding

    private var rendered: MeasurementUiState? = null
    private var cancelDialog: AlertDialog? = null
    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        binding = RmbtActivityMeasurementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars(top = true)
        binding.measurementBottomView.root.padForSystemBars(bottom = true, sides = false)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.buttonCancel.setOnClickListener { confirmCancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmCancel()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.outcome.collect { outcome ->
                        when (outcome) {
                            is MeasurementOutcome.Finished -> showResult(outcome.testUuid)
                            is MeasurementOutcome.Failed -> showError(outcome.error)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun render(state: MeasurementUiState) {
        val previous = rendered
        rendered = state

        if (previous?.network != state.network) {
            val network = state.network
            binding.networkName.text = when {
                network.kind == NetworkKind.ETHERNET -> getString(R.string.rmbt_home_ethernet)
                network.name != null -> network.name
                else -> networkKindLabel(network.kind)
            }
            binding.technologyName.text = networkKindLabel(network.kind)
        }

        val curve = binding.curveLayout
        if (previous?.state != state.state) curve.setMeasurementState(state.state)
        if (previous?.progress != state.progress || previous.state != state.state) curve.setTopProgress(state.progress)
        if (previous?.liveSpeedBps != state.liveSpeedBps) {
            curve.setBottomProgress(state.liveSpeedBps)
            curve.setBottomSpeedValue(state.liveSpeedBps)
        }

        val bottom = binding.measurementBottomView
        if (previous?.state != state.state) {
            MeasurementPhases.of(state.state, false)?.let { phase ->
                bottom.labelMeasurementState.text =
                    getString(R.string.rmbt_measurement_phase_step, phase.step, phase.total, getString(phase.title))
                bottom.textMeasurementPhaseHint.text = getString(phase.hint)
            }
            // Highlight each value the moment the test has finished measuring it.
            val finishedView = when (MeasurementPhases.finishedValue(previous?.state, state.state)) {
                MeasurementState.PING -> bottom.appCompatImageView1
                MeasurementState.DOWNLOAD -> bottom.textDownloadSpeed
                MeasurementState.UPLOAD -> bottom.textUploadSpeed
                else -> null
            }
            finishedView?.post { UiFeedback.pop(finishedView) }
        }
        if (previous?.pingNanos != state.pingNanos || previous == null) bottom.appCompatImageView1.setPing(state.pingNanos)
        if (previous?.downloadBps != state.downloadBps || previous == null) bottom.textDownloadSpeed.setDownload(state.downloadBps)
        if (previous?.uploadBps != state.uploadBps || previous == null) bottom.textUploadSpeed.setUpload(state.uploadBps)

        val chart = bottom.speedChartDownloadUpload
        val speedPhase = state.state == MeasurementState.DOWNLOAD || state.state == MeasurementState.UPLOAD
        chart.visibility = if (speedPhase || state.state == MeasurementState.INIT || state.state == MeasurementState.PING) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        if (previous?.state != state.state) chart.reset()
        if (speedPhase && previous?.chart != state.chart) chart.addGraphItems(state.chart)
    }

    private fun confirmCancel() {
        if (cancelDialog?.isShowing == true) return
        cancelDialog = AlertDialog.Builder(this)
            .setMessage(R.string.rmbt_title_cancel_measurement)
            .setPositiveButton(R.string.rmbt_text_cancel_measurement) { _, _ ->
                viewModel.cancel()
                finish()
            }
            .setNegativeButton(R.string.rmbt_text_continue_measurement, null)
            .show()
    }

    private fun showResult(testUuid: String) {
        UiFeedback.confirm(binding.root)
        ResultActivity.start(this, testUuid)
        finish()
    }

    /**
     * The outcome stays in the ViewModel, so after a rotation the new activity shows the dialog again; the old
     * one is dismissed in [onDestroy].
     */
    private fun showError(error: Throwable) {
        if (errorDialog?.isShowing == true) return
        // For the host app's developers: the dialog only says that the test failed.
        Log.w(TAG, "Speed test failed", error)
        cancelDialog?.dismiss()
        errorDialog = AlertDialog.Builder(this)
            .setMessage(R.string.rmbt_test_dialog_error_text)
            .setPositiveButton(R.string.rmbt_input_setting_dialog_ok) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // A dialog still showing when the activity goes (rotation) would leak its window.
    override fun onDestroy() {
        cancelDialog?.dismiss()
        errorDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RmbtSdk"

        fun start(context: Context) {
            context.startActivity(Intent(context, MeasurementActivity::class.java))
        }
    }
}
