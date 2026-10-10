import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// release 签名：口令与密钥路径从 keystore.properties 读取（该文件与 *.jks 均已在 .gitignore 中忽略）。
// 文件缺失时 release 仍可构建，但产物未签名、无法安装（故不阻断 CI/本地构建）。
// ⚠️ 签名密钥一旦丢失，将无法对已安装版本做覆盖升级，请务必备份 keystore/ 与 keystore.properties。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasSigningProps = keystorePropsFile.exists() &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !keystoreProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.minibook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minibook"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningProps) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // keystore.properties 存在时使用正式签名，否则退回未签名产物（assembleRelease 仍成功）
            signingConfig = signingConfigs.findByName("release")
            // 当前不做混淆/裁剪（proguard-rules.pro 的 keep 规则为备用配置，未实际生效）；
            // 如需开启：isMinifyEnabled = true + isShrinkResources = true，并重跑真机验证。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Room MigrationTestHelper 迁移测试：把导出的 schema JSON（app/schemas/）打包进 androidTest assets，
    // 供 createDatabase(v1)/runMigrationsAndValidate(v2) 读取（F-12 Tester 补迁移测试基建）。
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // JavaMail 与其它 jar 携带同名 NOTICE/LICENSE 元数据文件，打包期冲突，需排除
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

// Room schema 导出目录（TASK-003 起在 @Database 上 exportSchema=true 后首次编译生成 1.json 并入库）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose（版本由 BOM 统一管理）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX 核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.biometric)

    // Room & SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // OkHttp（F-15 云同步：手动同步的 HTTP 客户端；不引 Retrofit/Ktor/WorkManager，见 docs/14_cloud_sync.md §5.1）
    implementation(libs.okhttp)

    // JavaMail（F-13 邮件账单导入：QQ 邮箱 IMAP 只读拉取）
    implementation(libs.sun.mail.android)
    implementation(libs.sun.activation.android)

    // 单元测试（Developer 白盒）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    // Instrumented 测试（Tester 阶段 7 基线）
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Room MigrationTestHelper（F-12 迁移测试 1→2，Tester 补）
    androidTestImplementation(libs.androidx.room.testing)
}
