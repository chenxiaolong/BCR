package com.chiller3.bcr

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class FilenameTemplate private constructor(props: Properties) {
    private val components = arrayListOf<Component>()

    init {
        Log.d(TAG, "Filename template: $props")

        while (true) {
            val index = components.size
            val text = props.getProperty("filename.$index.text") ?: break
            val default = props.getProperty("filename.$index.default")
            val prefix = props.getProperty("filename.$index.prefix")
            val suffix = props.getProperty("filename.$index.suffix")

            components.add(Component(text, default, prefix, suffix))
        }

        if (components.isEmpty() || !isDate(components[0].text)) {
            throw IllegalArgumentException("The first filename component must be ${'$'}{date}")
        }

        Log.d(TAG, "Loaded filename components: $components")
    }

    fun evaluate(getVar: (String) -> String?): String {
        val varCache = hashMapOf<String, String?>()
        val getVarCached = { name: String ->
            varCache.getOrPut(name) {
                getVar(name)
            }
        }

        return buildString {
            for (c in components) {
                var text = evalVars(c.text, getVarCached)
                if (text.isEmpty() && c.default != null) {
                    text = evalVars(c.default, getVarCached)
                }
                if (text.isNotEmpty()) {
                    if (c.prefix != null) {
                        append(evalVars(c.prefix, getVarCached))
                    }
                    append(text)
                    if (c.suffix != null) {
                        append(evalVars(c.suffix, getVarCached))
                    }
                }
            }
        }
    }

    private data class Component(
        val text: String,
        val default: String?,
        val prefix: String?,
        val suffix: String?,
    )

    companion object {
        private val TAG = FilenameTemplate::class.java.simpleName

        private val VAR_PATTERN = Pattern.compile("""\${'$'}\{([^\}]+)\}""")

        private fun evalVarsIndexed(input: String, getVar: (Int, String) -> String?): String =
            StringBuffer().run {
                val m = VAR_PATTERN.matcher(input)
                var index = 0

                while (m.find()) {
                    val name = m.group(1)!!
                    val replacement = getVar(index, name)

                    m.appendReplacement(this, Matcher.quoteReplacement(replacement ?: ""))

                    ++index
                }

                m.appendTail(this)

                toString()
            }

        private fun evalVars(input: String, getVar: (String) -> String?): String =
            evalVarsIndexed(input) { _, name ->
                getVar(name)
            }

        private fun isDate(input: String): Boolean =
            evalVarsIndexed(input) { index, name ->
                when {
                    index == 0 && (name == "date" || name.startsWith("date:")) -> "ok"
                    else -> null
                }
            }.isNotEmpty()

        fun load(context: Context, allowCustom: Boolean): FilenameTemplate {
            val props = Properties()

            if (allowCustom) {
                val prefs = Preferences(context)
                val outputDir = prefs.outputDir?.let {
                    // Only returns null on API <21
                    DocumentFile.fromTreeUri(context, it)!!
                } ?: DocumentFile.fromFile(prefs.defaultOutputDir)

                Log.d(TAG, "Looking for custom filename template in: ${outputDir.uri}")

                val templateFile = outputDir.findFileFast("bcr.properties")
                if (templateFile != null) {
                    try {
                        Log.d(TAG, "Loading custom filename template: ${templateFile.uri}")

                        context.contentResolver.openInputStream(templateFile.uri)?.use {
                            props.load(it)
                            return FilenameTemplate(props)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load custom filename template", e)
                    }
                }
            } else {
                Log.d(TAG, "Inhibited loading of custom filename template")
            }

            Log.d(TAG, "Loading default filename template")

            context.resources.openRawResource(R.raw.filename_template).use {
                props.load(it)
                return FilenameTemplate(props)
            }
        }
    }
}
