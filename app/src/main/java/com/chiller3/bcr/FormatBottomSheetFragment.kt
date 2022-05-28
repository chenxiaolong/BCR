package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.FormatParamType
import com.chiller3.bcr.format.Formats
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

class FormatBottomSheetFragment : BottomSheetDialogFragment(),
    MaterialButtonToggleGroup.OnButtonCheckedListener, LabelFormatter, Slider.OnChangeListener,
    View.OnClickListener {
    private lateinit var formatParamTitle: TextView
    private lateinit var formatParam: Slider
    private lateinit var formatReset: MaterialButton
    private lateinit var formatNameGroup: MaterialButtonToggleGroup
    private val buttonIdToFormat = HashMap<Int, Format>()
    private val formatToButtonId = HashMap<Format, Int>()
    private lateinit var formatParamType: FormatParamType

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val bottomSheet = inflater.inflate(R.layout.format_bottom_sheet, container, false)

        formatParamTitle = bottomSheet.findViewById(R.id.format_param_title)

        formatParam = bottomSheet.findViewById(R.id.format_param)
        formatParam.setLabelFormatter(this)
        formatParam.addOnChangeListener(this)

        formatReset = bottomSheet.findViewById(R.id.format_reset)
        formatReset.setOnClickListener(this)

        formatNameGroup = bottomSheet.findViewById(R.id.format_name_group)!!

        for (format in Formats.all) {
            if (!format.supported) {
                continue
            }

            val button = layoutInflater.inflate(
                R.layout.format_bottom_sheet_button, formatNameGroup, false) as MaterialButton
            val id = ViewCompat.generateViewId()
            button.id = id
            button.text = format.name
            formatNameGroup.addView(button)
            buttonIdToFormat[id] = format
            formatToButtonId[format] = id
        }

        formatNameGroup.addOnButtonCheckedListener(this)

        refreshFormat()

        return bottomSheet
    }

    /**
     * Update UI based on currently selected format in the preferences.
     *
     * Calls [refreshParam] via [onButtonChecked].
     */
    private fun refreshFormat() {
        val (format, _) = Formats.fromPreferences(requireContext())
        formatNameGroup.check(formatToButtonId[format]!!)
    }

    /**
     * Update parameter title and slider to match format parameter specifications.
     */
    private fun refreshParam() {
        val (format, param) = Formats.fromPreferences(requireContext())
        formatParamType = format.paramType

        val titleResId = when (format.paramType) {
            FormatParamType.CompressionLevel -> R.string.bottom_sheet_compression_level
            FormatParamType.Bitrate -> R.string.bottom_sheet_bitrate
        }

        formatParamTitle.setText(titleResId)

        formatParam.valueFrom = format.paramRange.first.toFloat()
        formatParam.valueTo = format.paramRange.last.toFloat()
        formatParam.stepSize = format.paramStepSize.toFloat()

        formatParam.value = (param ?: format.paramDefault).toFloat()
    }

    override fun onButtonChecked(
        group: MaterialButtonToggleGroup?,
        checkedId: Int,
        isChecked: Boolean
    ) {
        if (isChecked) {
            Preferences.setFormatName(requireContext(), buttonIdToFormat[checkedId]!!.name)
            refreshParam()
        }
    }

    override fun getFormattedValue(value: Float): String =
        formatParamType.format(value.toUInt())

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            formatParam -> {
                val format = buttonIdToFormat[formatNameGroup.checkedButtonId]!!
                Preferences.setFormatParam(requireContext(), format.name, value.toUInt())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            formatReset -> {
                Preferences.resetAllFormats(requireContext())
                refreshFormat()
                // Need to explicitly refresh the parameter when the default format is already chosen
                refreshParam()
            }
        }
    }

    companion object {
        val TAG: String = FormatBottomSheetFragment::class.java.simpleName
    }
}