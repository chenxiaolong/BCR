package com.chiller3.bcr.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * A small wrapper around [ActivityResultContracts.OpenDocumentTree] that requests write-persistable
 * URIs when opening directories.
 */
class OpenPersistentDocumentTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = super.createIntent(context, input)

        intent.addFlags(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )

        return intent
    }
}
