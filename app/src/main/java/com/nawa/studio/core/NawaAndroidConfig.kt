package com.nawa.studio.core

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Validated project-level Android build settings. These settings never execute arbitrary Gradle text. */
data class NawaAndroidConfig(
    val label: String = "",
    val applicationId: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList()
) {
    fun validate(): NawaAndroidConfig {
        require(label.length <= 80 && label.none { it == '\u0000' || it == '\n' || it == '\r' }) {
            "اسم التطبيق في android.properties يجب ألا يتجاوز 80 حرفًا أو يحتوي سطرًا جديدًا."
        }
        require(applicationId.isBlank() || APPLICATION_ID.matches(applicationId)) {
            "معرّف التطبيق غير صالح. استخدم مثلًا com.example.myapp بأحرف صغيرة ونقاط."
        }
        require(VERSION_NAME.matches(versionName)) { "اسم الإصدار غير صالح. استخدم حروفًا وأرقامًا ونقاطًا وشرطات فقط." }
        require(versionCode in 1..2_100_000_000) { "رقم الإصدار يجب أن يكون بين 1 و2,100,000,000." }
        require(minSdk in 23..35) { "minSdk يجب أن يكون بين 23 و35 في إعدادات هذه النسخة." }
        require(targetSdk in minSdk..35) { "targetSdk يجب أن يكون مساويًا لـ minSdk أو أعلى منه، وبحد أقصى 35." }
        require(permissions.size <= 80 && permissions.all { PERMISSION.matches(it) }) {
            "تحتوي قائمة الصلاحيات على اسم غير صالح؛ استخدم اسم صلاحية كاملًا مثل android.permission.INTERNET."
        }
        require(permissions.distinct().size == permissions.size) { "قائمة الصلاحيات تحتوي عناصر مكررة." }
        require(dependencies.size <= 100 && dependencies.all { MAVEN_COORDINATE.matches(it) }) {
            "اعتمادية غير صالحة. اكتب كل اعتماد بصيغة group:artifact:version، ولا تضع شيفرة Gradle."
        }
        require(dependencies.distinct().size == dependencies.size) { "قائمة الاعتماديات تحتوي عناصر مكررة." }
        return copy(label = label.trim(), applicationId = applicationId.trim(), permissions = permissions.map(String::trim), dependencies = dependencies.map(String::trim))
    }

    fun serialize(): String {
        val clean = validate()
        val properties = Properties().apply {
            setProperty("label", clean.label)
            setProperty("applicationId", clean.applicationId)
            setProperty("versionName", clean.versionName)
            setProperty("versionCode", clean.versionCode.toString())
            setProperty("minSdk", clean.minSdk.toString())
            setProperty("targetSdk", clean.targetSdk.toString())
            setProperty("permissions", clean.permissions.joinToString(","))
            setProperty("dependencies", clean.dependencies.joinToString(","))
        }
        val writer = StringWriter()
        properties.store(writer, "NAWA Android build settings — edit values, never add Gradle code")
        return writer.toString()
    }

    companion object {
        private val APPLICATION_ID = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
        private val VERSION_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,31}")
        private val PERMISSION = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
        private val MAVEN_COORDINATE = Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+")

        fun parse(source: String): NawaAndroidConfig {
            if (source.isBlank()) return NawaAndroidConfig()
            val properties = Properties()
            properties.load(StringReader(source))
            fun integer(key: String, default: Int): Int = properties.getProperty(key, default.toString()).trim().toIntOrNull()
                ?: throw IllegalArgumentException("قيمة $key يجب أن تكون رقمًا صحيحًا.")
            fun list(key: String): List<String> = properties.getProperty(key, "")
                .split(',').map(String::trim).filter(String::isNotEmpty)
            return NawaAndroidConfig(
                label = properties.getProperty("label", "").trim(),
                applicationId = properties.getProperty("applicationId", "").trim(),
                versionName = properties.getProperty("versionName", "1.0.0").trim(),
                versionCode = integer("versionCode", 1),
                minSdk = integer("minSdk", 26),
                targetSdk = integer("targetSdk", 35),
                permissions = list("permissions"),
                dependencies = list("dependencies")
            ).validate()
        }

        fun defaultTemplate(): String = NawaAndroidConfig().serialize()
    }
}
