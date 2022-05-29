package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
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
    private lateinit var formatParamGroup: LinearLayout
    private lateinit var formatParamTitle: TextView
    private lateinit var formatParamSlider: Slider
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

        formatParamGroup = bottomSheet.findViewById(R.id.format_param_group)

        formatParamTitle = bottomSheet.findViewById(R.id.format_param_title)

        formatParamSlider = bottomSheet.findViewById(R.id.format_param_slider)
        formatParamSlider.setLabelFormatter(this)
        formatParamSlider.addOnChangeListener(this)

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

        formatParamGroup.isVisible = format.paramRange.first != format.paramRange.last

        formatParamTitle.setText(titleResId)

        formatParamSlider.valueFrom = format.paramRange.first.toFloat()
        formatParamSlider.valueTo = format.paramRange.last.toFloat()
        formatParamSlider.stepSize = format.paramStepSize.toFloat()
        formatParamSlider.value = (param ?: format.paramDefault).toFloat()

        // Needed due to a bug in the material3 library where the slider label does not disappear
        // when the slider visibility is set to View.GONE
        // https://github.com/material-components/material-components-android/issues/2726
        if (format.paramRange.first == format.paramRange.last) {
            val ensureLabelsRemoved = formatParamSlider.javaClass.superclass
                .getDeclaredMethod("ensureLabelsRemoved")
            ensureLabelsRemoved.isAccessible = true
            ensureLabelsRemoved.invoke(formatParamSlider)
        }
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
            formatParamSlider -> {
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