package tj.zehn.rmbt.ui.screen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.data.HistoryStore
import tj.zehn.rmbt.ui.databinding.RmbtFragmentHistoryBinding
import tj.zehn.rmbt.ui.databinding.RmbtItemHistoryBinding
import tj.zehn.rmbt.ui.model.TestRecord
import tj.zehn.rmbt.ui.util.HistoryTimeFormat
import tj.zehn.rmbt.ui.util.ResultValueFormat
import tj.zehn.rmbt.ui.util.getPingClassificationIcon
import tj.zehn.rmbt.ui.util.getSpeedDownloadClassification
import tj.zehn.rmbt.ui.util.getSpeedUploadClassificationIcon
import tj.zehn.rmbt.ui.util.padForSystemBars
import tj.zehn.rmbt.ui.util.setHistoryRating
import tj.zehn.rmbt.ui.util.setHistorySignalIcon
import java.util.Locale

/**
 * History copied from open-rmbt-android (HistoryFragment): the tests made on this device, newest
 * first; a tap opens the result. Can be embedded in any activity; [withBackButton] adds a back arrow
 * when it is a screen of its own.
 */
class RmbtHistoryFragment : RmbtFragment() {

    private var _binding: RmbtFragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = RmbtFragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.padForSystemBars(top = true)
        if (arguments?.getBoolean(ARG_BACK_BUTTON) == true) {
            binding.buttonBack.visibility = View.VISIBLE
            binding.buttonBack.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        }
        val adapter = HistoryAdapter { ResultActivity.start(requireContext(), it.testUuid) }
        binding.recyclerViewHistoryItems.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Nothing (no "empty" state either) until the history has been read from storage.
                HistoryStore.get(requireContext()).history.filterNotNull().collect { history ->
                    adapter.submitList(history)
                    binding.layoutHistoryEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
                    binding.viewHistoryHeader.root.visibility = if (history.isEmpty()) View.INVISIBLE else View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) setLightStatusBar(true)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isResumed) setLightStatusBar(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_BACK_BUTTON = "rmbt_back_button"

        /** The history as a screen of its own, with a back arrow before the title. */
        fun withBackButton(): RmbtHistoryFragment = RmbtHistoryFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_BACK_BUTTON, true) }
        }
    }
}

private class HistoryAdapter(
    private val onClick: (TestRecord) -> Unit,
) : ListAdapter<TestRecord, HistoryAdapter.Holder>(Diff) {

    class Holder(val binding: RmbtItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(RmbtItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val record = getItem(position)
        val context = holder.itemView.context
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        with(holder.binding) {
            imageSignal.setHistorySignalIcon(record.network, record.signalLevel)
            textTime.text = HistoryTimeFormat.format(
                record.timeMillis,
                record.timezone,
                System.currentTimeMillis(),
                context.getString(R.string.rmbt_history_today),
                context.getString(R.string.rmbt_history_yesterday),
            )
            textRating.setHistoryRating(record.downloadClass, record.uploadClass, record.pingClass)

            val download = speed(record.downloadMbps, locale, context.getString(R.string.rmbt_measurement_dash))
            val upload = speed(record.uploadMbps, locale, context.getString(R.string.rmbt_measurement_dash))
            val ping = if (record.pingMs > 0) ResultValueFormat.pingNumber(record.pingMs, locale) else context.getString(R.string.rmbt_measurement_dash)
            textDownload.text = download
            textDownload.setCompoundDrawablesWithIntrinsicBounds(getSpeedDownloadClassification(record.downloadClass), 0, 0, 0)
            textDownload.contentDescription =
                "${context.getString(R.string.rmbt_measurement_download)} $download ${context.getString(R.string.rmbt_speed_progress_units)}"
            textUpload.text = upload
            textUpload.setCompoundDrawablesWithIntrinsicBounds(getSpeedUploadClassificationIcon(record.uploadClass), 0, 0, 0)
            textUpload.contentDescription =
                "${context.getString(R.string.rmbt_measurement_upload)} $upload ${context.getString(R.string.rmbt_speed_progress_units)}"
            textPing.text = ping
            textPing.setCompoundDrawablesWithIntrinsicBounds(getPingClassificationIcon(record.pingClass), 0, 0, 0)
            textPing.contentDescription =
                "${context.getString(R.string.rmbt_measurement_ping)} $ping ${context.getString(R.string.rmbt_history_item_header_ms)}"
            root.setOnClickListener { onClick(record) }
        }
    }

    private fun speed(mbps: Double, locale: Locale, dash: String): String =
        if (mbps > 0) ResultValueFormat.speedNumber((mbps * 1000).toLong(), locale) else dash

    private object Diff : DiffUtil.ItemCallback<TestRecord>() {
        override fun areItemsTheSame(oldItem: TestRecord, newItem: TestRecord) = oldItem.testUuid == newItem.testUuid
        override fun areContentsTheSame(oldItem: TestRecord, newItem: TestRecord) = oldItem == newItem
    }
}
