package com.chiller3.bcr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView

abstract class PreferenceBaseFragment : PreferenceFragmentCompat() {
    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val view = super.onCreateRecyclerView(inflater, parent, savedInstanceState)

        view.clipToPadding = false

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )

            // This is a little bit ugly in landscape mode because the divider lines for categories
            // extend into the inset area. However, it's worth applying the left/right padding here
            // anyway because it allows the inset area to be used for scrolling instead of just
            // being a useless dead zone.
            v.updatePadding(
                bottom = insets.bottom,
                left = insets.left,
                right = insets.right,
            )

            WindowInsetsCompat.CONSUMED
        }

        return view
    }
}
