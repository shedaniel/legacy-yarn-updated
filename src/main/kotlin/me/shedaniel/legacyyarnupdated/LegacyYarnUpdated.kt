package me.shedaniel.legacyyarnupdated

import net.fabricmc.stitch.commands.tinyv2.TinyV2Reader
import net.fabricmc.stitch.commands.tinyv2.TinyV2Writer
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.properties.Delegates

lateinit var targetVersion: String
lateinit var legacyVersion: String

val tmp = File(System.getProperty("user.dir"), ".legacyyarnupdated")

fun main(args: Array<String>) {
    targetVersion = args[0]
    legacyVersion = args[1]
    tmp.deleteRecursively()
    tmp.mkdirs()
    val targetYarnJarUrl = URL("https://maven.fabricmc.net/net/fabricmc/yarn/$targetVersion/yarn-$targetVersion-v2.jar")
    println("downloading target yarn v2 from: $targetYarnJarUrl")
    var targetMappingsPath: Path by Delegates.notNull()
    val targetZipStream = ZipInputStream(targetYarnJarUrl.openStream())
    while (true) {
        val entry = targetZipStream.nextEntry ?: throw NullPointerException()
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
            targetMappingsPath = tmp.toPath().resolve("target.tiny")
            targetMappingsPath.toFile().writeBytes(targetZipStream.readBytes())
            break
        }
    }
    val targetMappings = TinyV2Reader.read(targetMappingsPath)

    val legacyYarnJarUrl = URL("https://maven.fabricmc.net/net/fabricmc/yarn/$legacyVersion/yarn-$legacyVersion-v2.jar")
    println("downloading legacy yarn v2 from: $legacyYarnJarUrl")
    var legacyMappingsPath: Path by Delegates.notNull()
    val legacyZipStream = ZipInputStream(legacyYarnJarUrl.openStream())
    while (true) {
        val entry = legacyZipStream.nextEntry ?: throw NullPointerException()
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "mappings.tiny") {
            legacyMappingsPath = tmp.toPath().resolve("legacy.tiny")
            legacyMappingsPath.toFile().writeBytes(legacyZipStream.readBytes())
            break
        }
    }
    val legacyMappings = TinyV2Reader.read(legacyMappingsPath)
    targetMappings.classEntries.forEach { targetClass ->
        val targetIntermediary = targetClass.classNames[0]
        val legacyClass = legacyMappings.classEntries.firstOrNull {
            it.classNames[0] == targetIntermediary
        } ?: return@forEach
        val targetClassName = targetClass.classNames[1]
        val legacyClassName = legacyClass.classNames[1]
        if (targetClassName.split('/').last() != legacyClassName.split('/').last()
                && targetMappings.classEntries.none {
                    it.classNames[1] == legacyClassName
                }) {
            println("$targetClassName -> $legacyClassName")
            targetClass.classNames[1] = legacyClassName
        }
        targetClass.fields.forEach fields@{ targetField ->
            val targetFieldIntermediary = targetField.fieldNames[0]
            val legacyField = legacyClass.fields.firstOrNull {
                it.fieldNames[0] == targetFieldIntermediary
            } ?: return@fields
            val targetFieldName = targetField.fieldNames[1]
            val legacyFieldName = legacyField.fieldNames[1]
            if (targetFieldName != legacyFieldName && targetClass.fields.none {
                        it.fieldNames[1] == legacyFieldName
                    }) {
                println("  $targetClassName.$targetFieldName -> $legacyClassName.$legacyFieldName")
                targetField.fieldNames[1] = legacyFieldName
            }
        }
        targetClass.methods.forEach methods@{ targetMethod ->
            val targetMethodIntermediary = targetMethod.methodNames[0]
            val legacyMethod = legacyClass.methods.firstOrNull {
                it.methodNames[0] == targetMethodIntermediary
            } ?: return@methods
            val targetMethodName = targetMethod.methodNames[1]
            val legacyMethodName = legacyMethod.methodNames[1]
            if (targetMethodName != legacyMethodName && targetClass.methods.none {
                        it.methodNames[1] == legacyMethodName
                    }) {
                println("  $targetClassName.$targetMethodName -> $legacyClassName.$legacyMethodName")
                targetMethod.methodNames[1] = legacyMethodName
            }
        }
    }

    println()
    println("writing mappings")
    val repackedPath = tmp.toPath().resolve("output.tiny")
    TinyV2Writer.write(targetMappings, repackedPath)

    println("repacking mappings")
    val path = tmp.parentFile.toPath().resolve("build/libs/yarn-${targetVersion}+legacy.${legacyVersion}-v2.jar")
    Files.deleteIfExists(path)
    ZipOutputStream(path.toFile().outputStream()).use { zipOutputStream ->
        val zipEntry = ZipEntry("mappings/mappings.tiny")
        zipOutputStream.putNextEntry(zipEntry)

        val bytes = repackedPath.toFile().readBytes()
        zipOutputStream.write(bytes, 0, bytes.size)
        zipOutputStream.closeEntry()
    }
    tmp.deleteRecursively()
}