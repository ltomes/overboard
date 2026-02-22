import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileOutputStream

plugins {
  id("com.android.application") version "8.13.2"
  id("app.cash.paparazzi") version "1.3.5"
}

dependencies {
  // Following versions of androidx.window require sdk version 23
  implementation("androidx.window:window-java:1.4.0")
  implementation("androidx.core:core:1.16.0") // Version 1.17.0 available with sdk 36
  testImplementation("junit:junit:4.13.2")
}

android {
  namespace = "com.overboard.keyboard"
  compileSdkVersion = "android-35"

  defaultConfig {
    applicationId = "com.overboard.keyboard"
    minSdk = 21
    targetSdk { version = release(35) }
    versionCode = 4
    versionName = "0.2.0"
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      java.srcDirs("srcs/com.overboard.keyboard", "vendor/cdict/java/juloo.cdict")
      res.srcDirs("res", "build/generated-resources")
      assets.srcDirs("assets")
    }

    named("test") {
      java.srcDirs("test")
    }
  }

  externalNativeBuild {
    ndkBuild {
      path = file("vendor/Android.mk")
    }
  }

  signingConfigs {
    // Debug builds will always be signed. If no environment variables are set, a default
    // keystore will be initialized by the task initDebugKeystore and used. This keystore
    // can be uploaded to GitHub secrets by following instructions in CONTRIBUTING.md
    // in order to always receive correctly signed debug APKs from the CI.
    named("debug") {
      storeFile = file(System.getenv("DEBUG_KEYSTORE") ?: "debug.keystore")
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "debug0"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "debug"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "debug0"
    }

    create("release") {
      val ks = System.getenv("RELEASE_KEYSTORE")
      if (ks != null) {
        storeFile = file(ks)
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      resValue("string", "app_name", "@string/app_name_release")
      signingConfig = signingConfigs["release"]
    }

    named("debug") {
      isMinifyEnabled = false
      isShrinkResources = false
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "@string/app_name_debug")
      resValue("bool", "debug_logs", "true")
      signingConfig = signingConfigs["debug"]
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

val buildKeyboardFont by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/special_font")
  val out = layout.projectDirectory.file("assets/special_font.ttf")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nBuilding assets/special_font.ttf") }
  workingDir = `in`
  val svgFiles = `in`.listFiles()!!.filter {
    it.isFile && it.name.endsWith(".svg")
  }.toTypedArray()
  commandLine("fontforge", "-lang=ff", "-script", "build.pe", out.asFile.absolutePath, *svgFiles)
}

val genEmojis by tasks.registering(Exec::class) {
  doFirst { println("\nGenerating res/raw/emojis.txt") }
  workingDir = projectDir
  commandLine("python3", "gen_emoji.py")
}

val genLayoutsList by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  outputs.file(projectDir.resolve("res/values/layouts.xml"))
  doFirst { println("\nGenerating res/values/layouts.xml") }
  workingDir = projectDir
  commandLine("python3", "gen_layouts.py")
}

val genMethodXml by tasks.registering(Exec::class) {
  val out = projectDir.resolve("res/xml/method.xml")
  inputs.file(projectDir.resolve("gen_method_xml.py"))
  inputs.file(projectDir.resolve("res/values/dictionaries.xml"))
  outputs.file(out)
  doFirst { println("\nGenerating res/xml/method.xml") }
  doFirst { standardOutput = FileOutputStream(out) }
  workingDir = projectDir
  commandLine("python3", "gen_method_xml.py")
}

val checkKeyboardLayouts by tasks.registering(Exec::class) {
  inputs.dir(projectDir.resolve("srcs/layouts"))
  inputs.file(projectDir.resolve("srcs/com.overboard.keyboard/KeyValue.java"))
  outputs.file(projectDir.resolve("check_layout.output"))
  doFirst { println("\nChecking layouts") }
  workingDir = projectDir
  commandLine("python3", "check_layout.py")
}

val compileComposeSequences by tasks.registering(Exec::class) {
  val `in` = projectDir.resolve("srcs/compose")
  val out = projectDir.resolve("srcs/com.overboard.keyboard/ComposeKeyData.java")
  inputs.dir(`in`)
  outputs.file(out)
  doFirst { println("\nGenerating $out") }
  val sequences = `in`.listFiles { it: File ->
    !it.name.endsWith(".py") && !it.name.endsWith(".md")
  }!!.map { it.absolutePath }.toTypedArray()
  workingDir = projectDir
  commandLine("python3", `in`.resolve("compile.py").absolutePath, *sequences)
  doFirst { standardOutput = FileOutputStream(out) }
}

tasks.withType(Test::class).configureEach {
  dependsOn(genLayoutsList, checkKeyboardLayouts, compileComposeSequences, genMethodXml)
}

val initDebugKeystore by tasks.registering(Exec::class) {
  val keystoreFile = file("debug.keystore")
  val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
  doFirst { println("Initializing default debug keystore") }
  onlyIf { !keystoreFile.exists() }
  commandLine("${javaHome}/bin/keytool", "-genkeypair", "-dname", "cn=d, ou=e, o=b, c=ug", "-alias", "debug", "-keypass", "debug0", "-keystore", keystoreFile.absolutePath, "-keyalg", "rsa", "-storepass", "debug0", "-validity", "10000")
}

// latn_qwerty_us is used as a raw resource by the custom layout option.
val copyRawQwertyUS by tasks.registering(Copy::class) {
  from("srcs/layouts/latn_qwerty_us.xml")
  into("build/generated-resources/raw")
}

val copyLayoutDefinitions by tasks.registering(Copy::class) {
  from("srcs/layouts")
  include("*.xml")
  into("build/generated-resources/xml")
}

tasks.named("preBuild") {
  dependsOn(initDebugKeystore, copyRawQwertyUS, copyLayoutDefinitions)
  // 'mustRunAfter' defines ordering between tasks (which is required by
  // Gradle) but doesn't create a dependency. These rules update files that are
  // checked in the repository that don't need to be updated during regular
  // builds.
  mustRunAfter(genEmojis, genLayoutsList, compileComposeSequences, genMethodXml)
}
