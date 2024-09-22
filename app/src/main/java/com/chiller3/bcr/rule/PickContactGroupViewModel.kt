package com.chiller3.bcr.rule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chiller3.bcr.ContactGroupInfo
import com.chiller3.bcr.findContactGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickContactGroupViewModel(application: Application) : AndroidViewModel(application) {
    private val _groups = MutableStateFlow<List<ContactGroupInfo>>(emptyList())
    val groups: StateFlow<List<ContactGroupInfo>> = _groups

    init {
        refreshGroups()
    }

    private fun refreshGroups() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val groups = try {
                    findContactGroups(getApplication())
                        .asSequence()
                        .sortedWith { o1, o2 ->
                            compareValuesBy(
                                o1,
                                o2,
                                { it.title },
                                { it.rowId },
                                { it.sourceId },
                            )
                        }
                        .toList()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to list all contact groups", e)
                    return@withContext
                }

                _groups.update { groups }
            }
        }
    }

    companion object {
        private val TAG = PickContactGroupViewModel::class.java.simpleName
    }
}
