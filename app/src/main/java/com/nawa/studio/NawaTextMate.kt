package com.nawa.studio

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource

/** Loads the bundled NawaScript/Kotlin TextMate grammars once; editing remains offline. */
object NawaTextMate {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        runCatching {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themeName = "nawa-dark"
            val themePath = "textmate/$themeName.json"
            val stream = FileProviderRegistry.getInstance().tryGetInputStream(themePath)
                ?: error("Missing bundled editor theme")
            ThemeRegistry.getInstance().loadTheme(
                ThemeModel(IThemeSource.fromInputStream(stream, themePath, null), themeName).apply { isDark = true }
            )
            ThemeRegistry.getInstance().setTheme(themeName)
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            initialized = true
        }
    }

    fun applyTo(editor: CodeEditor, path: String) {
        if (!initialized || editor.tag == path) return
        runCatching {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            val scope = when (path.substringAfterLast('.', "").lowercase()) {
                "nawa" -> "source.nawa"
                "kt", "kts" -> "source.kotlin"
                "java" -> "source.java"
                "js", "jsx", "mjs", "cjs" -> "source.js"
                "json" -> "source.json"
                "xml", "html", "htm", "xhtml", "svg" -> "text.xml"
                "css" -> "source.css"
                "py", "pyw" -> "source.python"
                "sh", "bash", "zsh" -> "source.shell"
                "c", "h", "cc", "cpp", "cxx", "hh", "hpp", "hxx" -> "source.cpp"
                "md", "markdown", "mdown", "mkd" -> "text.markdown"
                "yaml", "yml" -> "source.yaml"
                "sql" -> "source.sql"
                "toml" -> "source.toml"
                else -> "text.plain"
            }
            editor.setEditorLanguage(TextMateLanguage.create(scope, true))
            editor.tag = path
        }
    }
}
