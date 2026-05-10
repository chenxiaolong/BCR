/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import com.chiller3.bcr.PreferenceBaseFragment
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.R
import com.chiller3.bcr.dialog.FormatChoiceDialogFragment
import com.chiller3.bcr.dialog.FormatParamDialogFragment
import com.chiller3.bcr.dialog.FormatSampleRateDialogFragment
import com.chiller3.bcr.format.Format
import com.chiller3.bcr.format.NoParamInfo
import com.chiller3.bcr.format.RangedParamInfo
import com.chiller3.bcr.format.RangedParamType
import com.chiller3.bcr.view.LongClickablePreference
import com.chiller3.bcr.view.OnPreferenceLongClickListener

class OutputFormatFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener,
    OnPreferenceLongClickListener, FragmentResultListener {
    override val requestTag: String = OutputFormatFragment::class.java.simpleName

    private lateinit var prefs: Preferences
    private lateinit var prefOutputFormat: LongClickablePreference
    private lateinit var prefFormatParam: LongClickablePreference
    private lateinit var prefSampleRate: LongClickablePreference
    private lateinit var prefAudioSource: LongClickablePreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()

        // Not strictly necessary since all preferences here are non-persistent.
        preferenceManager.setStorageDeviceProtected()
        setPreferencesFromResource(R.xml.preferences_output_format, rootKey)

        prefs = Preferences(context)

        prefOutputFormat = findPreference(Preferences.PREF_OUTPUT_FORMAT)!!
        prefOutputFormat.onPreferenceClickListener = this
        prefOutputFormat.onPreferenceLongClickListener = this

        prefFormatParam = findPreference(Preferences.PREF_FORMAT_PARAM)!!
        prefFormatParam.onPreferenceClickListener = this
        prefFormatParam.onPreferenceLongClickListener = this

        prefSampleRate = findPreference(Preferences.PREF_SAMPLE_RATE)!!
        prefSampleRate.onPreferenceClickListener = this
        prefSampleRate.onPreferenceLongClickListener = this

        prefAudioSource = findPreference(Preferences.PREF_AUDIO_SOURCE)!!
        prefAudioSource.onPreferenceClickListener = this
        prefAudioSource.onPreferenceLongClickListener = this

        refreshFormat()

        for (key in arrayOf(
            FormatChoiceDialogFragment.TAG,
            FormatParamDialogFragment.TAG,
            FormatSampleRateDialogFragment.TAG,
        )) {
            parentFragmentManager.setFragmentResultListener(key, this, this)
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.reset, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                R.id.reset -> {
                    prefs.resetAllFormats()
                    refreshFormat()
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshFormat() {
        val context = requireContext()

        val savedFormat = Format.fromPreferences(prefs)
        prefOutputFormat.summary = savedFormat.format.name

        val selectedParam = savedFormat.param ?: savedFormat.format.paramInfo.default
        prefFormatParam.summary = savedFormat.format.paramInfo.format(context, selectedParam)

        when (val info = savedFormat.format.paramInfo) {
            is RangedParamInfo -> {
                prefFormatParam.isVisible = true

                prefFormatParam.setTitle(when (info.type) {
                    RangedParamType.CompressionLevel ->
                        R.string.pref_format_param_desc_compression_level
                    RangedParamType.Bitrate -> R.string.pref_format_param_desc_bitrate
                })
            }
            NoParamInfo -> {
                prefFormatParam.isVisible = false
            }
        }

        val selectedSampleRate = savedFormat.sampleRate ?: savedFormat.format.sampleRateInfo.default
        prefSampleRate.summary = savedFormat.format.sampleRateInfo.format(context, selectedSampleRate)

        prefAudioSource.summary = buildString {
            append(getString(savedFormat.audioSource.nameResId))

            if (savedFormat.audioSource.isStereo) {
                append("\n\n")
                append(getString(R.string.audio_source_stereo_warning))
            }
        }
    }

    override fun onFragmentResult(requestKey: String, bundle: Bundle) {
        when (requestKey) {
            FormatChoiceDialogFragment.TAG -> {
                val result = BundleCompat.getParcelable(
                    bundle,
                    FormatChoiceDialogFragment.RESULT_RESULT,
                    FormatChoiceDialogFragment.Result::class.java,
                ) ?: return

                when (result) {
                    is FormatChoiceDialogFragment.Result.SelectedFormat -> {
                        prefs.format = result.format
                        refreshFormat()
                    }
                    is FormatChoiceDialogFragment.Result.SelectedParam -> {
                        if (result.param != null) {
                            prefs.setFormatParam(result.format, result.param)
                            refreshFormat()
                        } else {
                            FormatParamDialogFragment.newInstance(result.format).show(
                                parentFragmentManager.beginTransaction(),
                                FormatParamDialogFragment.TAG,
                            )
                        }
                    }
                    is FormatChoiceDialogFragment.Result.SelectedSampleRate -> {
                        if (result.sampleRate != null) {
                            prefs.setFormatSampleRate(result.format, result.sampleRate)
                            refreshFormat()
                        } else {
                            FormatSampleRateDialogFragment.newInstance(result.format).show(
                                parentFragmentManager.beginTransaction(),
                                FormatSampleRateDialogFragment.TAG,
                            )
                        }
                    }
                    is FormatChoiceDialogFragment.Result.SelectedAudioSource -> {
                        prefs.audioSource = result.audioSource
                        refreshFormat()
                    }
                }
            }
            FormatParamDialogFragment.TAG -> {
                val (format, value) = FormatParamDialogFragment.getResult(bundle) ?: return

                prefs.setFormatParam(format, value)
                refreshFormat()
            }
            FormatSampleRateDialogFragment.TAG -> {
                val (format, value) = FormatSampleRateDialogFragment.getResult(bundle) ?: return

                prefs.setFormatSampleRate(format, value)
                refreshFormat()
            }
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference) {
            prefOutputFormat -> {
                val action = FormatChoiceDialogFragment.Action.SelectFormat

                FormatChoiceDialogFragment.newInstance(action).show(
                    parentFragmentManager.beginTransaction(),
                    FormatChoiceDialogFragment.TAG,
                )

                return true
            }
            prefFormatParam -> {
                val savedFormat = Format.fromPreferences(prefs)
                val action = FormatChoiceDialogFragment.Action.SelectParam(savedFormat.format)

                FormatChoiceDialogFragment.newInstance(action).show(
                    parentFragmentManager.beginTransaction(),
                    FormatChoiceDialogFragment.TAG,
                )

                return true
            }
            prefSampleRate -> {
                val savedFormat = Format.fromPreferences(prefs)
                val action = FormatChoiceDialogFragment.Action.SelectSampleRate(savedFormat.format)

                FormatChoiceDialogFragment.newInstance(action).show(
                    parentFragmentManager.beginTransaction(),
                    FormatChoiceDialogFragment.TAG,
                )

                return true
            }
            prefAudioSource -> {
                val savedFormat = Format.fromPreferences(prefs)
                val action = FormatChoiceDialogFragment.Action.SelectAudioSource(savedFormat.format)

                FormatChoiceDialogFragment.newInstance(action).show(
                    parentFragmentManager.beginTransaction(),
                    FormatChoiceDialogFragment.TAG,
                )

                return true
            }
        }

        return false
    }

    override fun onPreferenceLongClick(preference: Preference): Boolean {
        when (preference) {
            prefOutputFormat -> {
                prefs.format = null
                refreshFormat()
                return true
            }
            prefFormatParam -> {
                val savedFormat = Format.fromPreferences(prefs)
                prefs.setFormatParam(savedFormat.format, null)
                refreshFormat()
                return true
            }
            prefSampleRate -> {
                val savedFormat = Format.fromPreferences(prefs)
                prefs.setFormatSampleRate(savedFormat.format, null)
                refreshFormat()
                return true
            }
            prefAudioSource -> {
                prefs.audioSource = null
                refreshFormat()
                return true
            }
        }

        return false
    }
}
