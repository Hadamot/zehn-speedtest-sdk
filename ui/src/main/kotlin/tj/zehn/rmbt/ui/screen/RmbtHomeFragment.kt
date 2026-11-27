package tj.zehn.rmbt.ui.screen

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.RmbtUi
import tj.zehn.rmbt.ui.data.HistoryStore
import tj.zehn.rmbt.ui.data.NetworkMonitor
import tj.zehn.rmbt.ui.data.NetworkState
import tj.zehn.rmbt.ui.data.locationPermissionsToRequest
import tj.zehn.rmbt.ui.databinding.RmbtFragmentHomeBinding
import tj.zehn.rmbt.ui.model.LastTestCard
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.util.ResultExplanations
import tj.zehn.rmbt.ui.util.UiFeedback
import tj.zehn.rmbt.ui.util.networkKindLabel
import tj.zehn.rmbt.ui.util.padForSystemBars
import java.util.Locale

/**
 * Start screen copied from open-rmbt-android (HomeFragment): the connection, the start button with
 * spreading rings, the wave and the last test card. Can be embedded in any activity; a host with a
 * floating bottom bar keeps the card above it with [setContentBottomPadding].
 */
class RmbtHomeFragment : RmbtFragment() {

    private var _binding: RmbtFragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var network: NetworkState = NetworkState.OFFLINE
    private val handler = Handler(Looper.getMainLooper())
    private var infoWindowDismissed = false
    private var contentBottomPadding = 0
    private var cardBottomMargin = 0

    /** The test starts once the user has answered; without the permission it runs without a map point. */
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (isAdded) MeasurementActivity.start(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = RmbtFragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.padForSystemBars(top = true)
        cardBottomMargin = (binding.lastTestCard.layoutParams as MarginLayoutParams).bottomMargin
        applyContentBottomPadding()
        val monitor = NetworkMonitor(requireContext())
        val store = HistoryStore.get(requireContext())

        UiFeedback.pressScale(binding.ivSignalLevel)
        binding.ivSignalLevel.setOnClickListener {
            UiFeedback.tap(it)
            if (network.connected) {
                startTest()
            } else {
                AlertDialog.Builder(binding.root.context)
                    .setMessage(R.string.rmbt_home_no_internet_connection)
                    .setPositiveButton(R.string.rmbt_input_setting_dialog_ok, null)
                    .show()
            }
        }
        binding.tvInfo.setOnClickListener {
            infoWindowDismissed = true
            it.visibility = View.GONE
        }
        UiFeedback.pressScale(binding.lastTestCard)
        binding.lastTestCard.setOnClickListener {
            val card = store.history.value?.firstOrNull() ?: return@setOnClickListener
            UiFeedback.tap(it)
            ResultActivity.start(requireContext(), card.testUuid)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { monitor.states().collect(::render) }
                launch {
                    store.history.collect { history ->
                        // Not shown until the history has been read from storage.
                        binding.lastTestCard.show(history?.let { LastTestCard.after(it, locale()) } ?: LastTestCard.Hidden)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStartButtonAnimation()
        updateStatusBar()
        // The hint "tap to start" appears when the user has not started a test for a while.
        handler.postDelayed({
            if (_binding != null && network.connected && !infoWindowDismissed) binding.tvInfo.visibility = View.VISIBLE
        }, INFO_WINDOW_TIME_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        stopStartButtonAnimation()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) updateStatusBar()
    }

    /**
     * Extra space in px under the content, so a bar the host draws over the bottom of this screen
     * (e.g. a floating navigation bar and the system navigation bar under it) does not cover the
     * last test card. The background still runs to the bottom edge.
     */
    fun setContentBottomPadding(px: Int) {
        if (px == contentBottomPadding) return
        contentBottomPadding = px
        applyContentBottomPadding()
    }

    /** Asks for the location first, once per start, when the host app declares it and holds none. */
    private fun startTest() {
        val ask = if (RmbtUi.config.requestLocationPermission) locationPermissionsToRequest(requireContext()) else emptyArray()
        if (ask.isEmpty()) MeasurementActivity.start(requireContext()) else askLocation.launch(ask)
    }

    private fun applyContentBottomPadding() {
        val binding = _binding ?: return
        binding.lastTestCard.updateLayoutParams<MarginLayoutParams> { bottomMargin = cardBottomMargin + contentBottomPadding }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: NetworkState) {
        network = state
        val connected = state.connected
        with(binding) {
            llMain.setBackgroundResource(if (connected) R.drawable.rmbt_bg_splash else R.drawable.rmbt_bg_gray)
            tvTitle.setImageResource(if (connected) R.drawable.rmbt_ic_brand_logo_white else R.drawable.rmbt_ic_brand_logo)
            ivIsConnectedCircle.setImageResource(
                if (connected) R.drawable.rmbt_bg_home_with_internet_circle else R.drawable.rmbt_bg_home_without_internet_circle
            )
            setSignalIcon(state)
            ivTechnology.setImageResource(if (state.kind == NetworkKind.ETHERNET && connected) R.drawable.rmbt_ic_label_ethernet else 0)

            val kindLabel = requireContext().networkKindLabel(state.kind)
            tvNetworkType.text = if (connected && state.name != null) kindLabel else ""
            tvNetworkType.setTextColor(
                ContextCompat.getColor(requireContext(), if (connected) R.color.rmbt_text_white_transparency_40 else R.color.rmbt_text_gray_transparency_40)
            )
            tvNetworkName.text = if (connected) state.name ?: kindLabel else getString(R.string.rmbt_home_no_internet_connection)
            tvNetworkName.setTextColor(
                ContextCompat.getColor(requireContext(), if (connected) android.R.color.white else R.color.rmbt_text_dark_gray)
            )
            val level = state.signalLevel
            tvSignalQuality.visibility = if (connected && level != null) View.VISIBLE else View.GONE
            level?.let { tvSignalQuality.setText(ResultExplanations.signalQuality(it)) }
            tvLblNoConnection.visibility = if (connected) View.GONE else View.VISIBLE
            if (!connected) tvInfo.visibility = View.GONE
            waveView.waveEnabled = connected
        }
        updateStartButtonAnimation()
        updateStatusBar()
    }

    /** Network icon in the start button by type and signal level (DataBindingAdapters.setIcon). */
    private fun setSignalIcon(state: NetworkState) {
        val button = binding.ivSignalLevel
        button.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.rmbt_colorAccent))
        val level = state.signalLevel
        val icon = when {
            !state.connected -> {
                button.imageTintList = null
                R.drawable.rmbt_ic_no_internet
            }
            state.kind == NetworkKind.WIFI && level != null -> when (level) {
                2 -> R.drawable.rmbt_ic_wifi_2
                3 -> R.drawable.rmbt_ic_wifi_3
                4 -> R.drawable.rmbt_ic_wifi_4
                else -> R.drawable.rmbt_ic_wifi_1
            }
            state.kind == NetworkKind.CELLULAR && level != null -> when (level) {
                2 -> R.drawable.rmbt_ic_mobile_2
                3 -> R.drawable.rmbt_ic_mobile_3
                4 -> R.drawable.rmbt_ic_mobile_4
                else -> R.drawable.rmbt_ic_mobile_1
            }
            state.kind == NetworkKind.ETHERNET -> R.drawable.rmbt_ic_ethernet_home
            else -> R.drawable.rmbt_ic_signal_unknown
        }
        button.setImageResource(icon)
    }

    /** The button "breathes" and rings spread from it while a test can be started. */
    private fun updateStartButtonAnimation() {
        val binding = _binding ?: return
        if (isResumed && network.connected) {
            UiFeedback.startPulse(binding.ivIsConnectedCircle, START_BUTTON_PULSE_SCALE)
            UiFeedback.startBeacon(binding.vStartHalo1, START_HALO_FROM_SCALE, START_HALO_TO_SCALE)
            UiFeedback.startBeacon(binding.vStartHalo2, START_HALO_FROM_SCALE, START_HALO_TO_SCALE, START_HALO_STAGGER_MS)
        } else {
            stopStartButtonAnimation()
        }
    }

    private fun stopStartButtonAnimation() {
        val binding = _binding ?: return
        UiFeedback.stopPulse(binding.ivIsConnectedCircle)
        UiFeedback.stopBeacon(binding.vStartHalo1)
        UiFeedback.stopBeacon(binding.vStartHalo2)
    }

    /** Light icons on the blue background, dark icons on the grey offline background. */
    private fun updateStatusBar() {
        if (isResumed && !isHidden) setLightStatusBar(!network.connected)
    }

    private fun locale(): Locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()

    private companion object {
        const val INFO_WINDOW_TIME_MS = 2_000L
        const val START_BUTTON_PULSE_SCALE = 1.04f
        const val START_HALO_FROM_SCALE = 0.78f
        const val START_HALO_TO_SCALE = 1.05f
        const val START_HALO_STAGGER_MS = 1_000L
    }
}
