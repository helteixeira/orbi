package app.wristmind.mobile

import android.content.Context
import app.wristmind.core.AppConfig
import app.wristmind.core.ConfigStore
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

const val CAMINHO_CONFIG = "/wristmind/config"
const val URL_PROJETO = "https://github.com/helteixeira/wristmind"
const val URL_VERSOES = "https://github.com/helteixeira/wristmind/releases/latest"
const val URL_APK_RELOGIO = "https://github.com/helteixeira/wristmind/releases/latest/download/WristMindRelogio.apk"

/** Nomes dos relógios ligados a este telemóvel. */
suspend fun relogiosLigados(ctx: Context): List<String> =
    Wearable.getNodeClient(ctx).connectedNodes.await().map { it.displayName }

/** Envia a configuração para o relógio. Devolve o número de relógios ligados. */
suspend fun enviarParaRelogio(ctx: Context, config: AppConfig): Int {
    val pedido = PutDataMapRequest.create(CAMINHO_CONFIG).apply {
        dataMap.putString("json", ConfigStore.paraJson(config))
        dataMap.putLong("momento", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(ctx).putDataItem(pedido).await()
    return Wearable.getNodeClient(ctx).connectedNodes.await().size
}
