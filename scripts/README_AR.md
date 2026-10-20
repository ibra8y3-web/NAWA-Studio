# أدوات البناء

- `verify_android_build.sh`: يشترط Gradle وAndroid SDK Platform 35، ثم يشغّل `testDebugUnitTest` و`assembleDebug`، ويتحقق من APK والتوقيع والبصمة. السكربت لا يثبت الأدوات ولا يتجاوز الإخفاقات؛ استخدم GitHub Actions إذا لم تتوفر بيئة بناء محلية.
