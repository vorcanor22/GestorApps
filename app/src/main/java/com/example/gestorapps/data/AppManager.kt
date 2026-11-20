
package com.example.gestorapps.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.storage.StorageManager
import java.util.Date

data class ManagedApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val versionName: String,
    val versionCode: Long,
    val installDate: Long,
    val lastUpdateDate: Long,
    val appSize: Long?,
    val dataSize: Long?,
    val cacheSize: Long?,
    val totalSize: Long?
)

object AppManager {

    /**
     * Comprueba si el usuario ha concedido el acceso
     * a estadísticas de uso.
     */
    fun hasUsagePermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
            as AppOpsManager

        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )

        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Obtiene todas las apps instaladas que sean visibles
     * para nuestra aplicación.
     */
    fun getInstalledApps(context: Context): List<ManagedApp> {

        val pm = context.packageManager

        val installedApps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA
        )

        return installedApps.map { appInfo ->
            createManagedApp(context, pm, appInfo)
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Convierte ApplicationInfo en ManagedApp,
     * obteniendo icono, versión y fechas.
     */
    private fun createManagedApp(
        context: Context,
        pm: PackageManager,
        appInfo: ApplicationInfo
    ): ManagedApp {

        val packageName = appInfo.packageName

        val name = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        val icon = try {
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }

        val packageInfo = try {
            pm.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "Desconocida"

        val versionCode = packageInfo?.longVersionCode ?: 0L

        val installDate = packageInfo?.firstInstallTime ?: 0L

        val lastUpdateDate = packageInfo?.lastUpdateTime ?: 0L

        val storage = getAppStorage(context, packageName)

        return ManagedApp(
            name = name,
            packageName = packageName,
            icon = icon,
            versionName = versionName,
            versionCode = versionCode,
            installDate = installDate,
            lastUpdateDate = lastUpdateDate,
            appSize = storage?.appBytes,
            dataSize = storage?.dataBytes,
            cacheSize = storage?.cacheBytes,
            totalSize = storage?.totalBytes
        )
    }

    /**
     * Obtiene el tamaño de una aplicación usando StorageStatsManager.
     *
     * Requiere acceso especial a estadísticas de uso.
     * Puede devolver null si Android deniega la consulta.
     */
    private fun getAppStorage(
        context: Context,
        packageName: String
    ): AppStorage? {

        if (!hasUsagePermission(context)) {
            return null
        }

        val statsManager = context.getSystemService(
            Context.STORAGE_STATS_SERVICE
        ) as StorageStatsManager

        return try {
            val stats = statsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                android.os.Process.myUserHandle()
            )

            val appBytes = stats.appBytes
            val dataBytes = stats.dataBytes
            val cacheBytes = stats.cacheBytes

            AppStorage(
                appBytes = appBytes,
                dataBytes = dataBytes,
                cacheBytes = cacheBytes,
                totalBytes = appBytes + dataBytes + cacheBytes
            )

        } catch (e: Exception) {
            // Acceso denegado, paquete no disponible,
            // error del sistema o almacenamiento inaccesible.
            null
        }
    }

    /**
     * Devuelve la fecha de instalación como Date.
     */
    fun getInstallDate(app: ManagedApp): Date? {
        return if (app.installDate > 0L) {
            Date(app.installDate)
        } else {
            null
        }
    }

    /**
     * Devuelve la última fecha de actualización como Date.
     */
    fun getLastUpdateDate(app: ManagedApp): Date? {
        return if (app.lastUpdateDate > 0L) {
            Date(app.lastUpdateDate)
        } else {
            null
        }
    }

    /**
     * Convierte bytes a formato legible.
     */
    fun formatSize(bytes: Long?): String {
        if (bytes == null || bytes < 0L) {
            return "No disponible"
        }

        val kb = bytes / 1024.0

        if (kb < 1024) {
            return String.format("%.1f KB", kb)
        }

        val mb = kb / 1024.0

        if (mb < 1024) {
            return String.format("%.1f MB", mb)
        }

        val gb = mb / 1024.0

        return String.format("%.2f GB", gb)
    }

    /**
     * Datos internos de almacenamiento.
     */
    private data class AppStorage(
        val appBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
        val totalBytes: Long
    )
}
