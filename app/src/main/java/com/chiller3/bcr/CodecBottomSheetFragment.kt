package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.chiller3.bcr.codec.Codec
import com.chiller3.bcr.codec.CodecParamType
import com.chiller3.bcr.codec.Codecs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

class CodecBottomSheetFragment : BottomSheetDialogFragment(),
    MaterialButtonToggleGroup.OnButtonCheckedListener, LabelFormatter, Slider.OnChangeListener,
    View.OnClickListener {
    private lateinit var codecParamTitle: TextView
    private lateinit var codecParam: Slider
    private lateinit var codecReset: MaterialButton
    private lateinit var codecNameGroup: MaterialButtonToggleGroup
    private val buttonIdToCodec = HashMap<Int, Codec>()
    private val codecToButtonId = HashMap<Codec, Int>()
    private lateinit var codecParamType: CodecParamType

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val bottomSheet = inflater.inflate(R.layout.codec_bottom_sheet, container, false)

        codecParamTitle = bottomSheet.findViewById(R.id.codec_param_title)

        codecParam = bottomSheet.findViewById(R.id.codec_param)
        codecParam.setLabelFormatter(this)
        codecParam.addOnChangeListener(this)

        codecReset = bottomSheet.findViewById(R.id.codec_reset)
        codecReset.setOnClickListener(this)

        codecNameGroup = bottomSheet.findViewById(R.id.codec_name_group)!!

        for (codec in Codecs.all) {
            if (!codec.supported) {
                continue
            }

            val button = layoutInflater.inflate(
                R.layout.codec_bottom_sheet_button, codecNameGroup, false) as MaterialButton
            val id = ViewCompat.generateViewId()
            button.id = id
            button.text = codec.name
            codecNameGroup.addView(button)
            buttonIdToCodec[id] = codec
            codecToButtonId[codec] = id
        }

        codecNameGroup.addOnButtonCheckedListener(this)

        refreshCodec()

        return bottomSheet
    }

    /**
     * Update UI based on currently selected codec in the preferences.
     *
     * Calls [refreshParam] via [onButtonChecked].
     */
    private fun refreshCodec() {
        val (codec, _) = Codecs.fromPreferences(requireContext())
        codecNameGroup.check(codecToButtonId[codec]!!)
    }

    /**
     * Update parameter title and slider to match codec parameter specifications.
     */
    private fun refreshParam() {
        val (codec, param) = Codecs.fromPreferences(requireContext())
        codecParamType = codec.paramType

        val titleResId = when (codec.paramType) {
            CodecParamType.CompressionLevel -> R.string.bottom_sheet_compression_level
            CodecParamType.Bitrate -> R.string.bottom_sheet_bitrate
        }

        codecParamTitle.setText(titleResId)

        codecParam.valueFrom = codec.paramRange.first.toFloat()
        codecParam.valueTo = codec.paramRange.last.toFloat()
        codecParam.stepSize = codec.paramStepSize.toFloat()

        codecParam.value = (param ?: codec.paramDefault).toFloat()
    }

    override fun onButtonChecked(
        group: MaterialButtonToggleGroup?,
        checkedId: Int,
        isChecked: Boolean
    ) {
        if (isChecked) {
            Preferences.setCodecName(requireContext(), buttonIdToCodec[checkedId]!!.name)
            refreshParam()
        }
    }

    override fun getFormattedValue(value: Float): String =
        codecParamType.format(value.toUInt())

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            codecParam -> {
                val codec = buttonIdToCodec[codecNameGroup.checkedButtonId]!!
                Preferences.setCodecParam(requireContext(), codec.name, value.toUInt())
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            codecReset -> {
                Preferences.resetAllCodecs(requireContext())
                refreshCodec()
            }
        }
    }

    companion object {
        val TAG = CodecBottomSheetFragment::class.java.simpleName
    }
}