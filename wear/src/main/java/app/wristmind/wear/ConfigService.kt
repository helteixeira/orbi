package app.wristmind.wear

import app.wristmind.core.ConfigStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

const val CAMINHO_CONFIG = "/wristmind/config"

/** Recebe a configuração enviada pelo telemóvel, mesmo com a app fechada. */
class ConfigService : WearableListenerService() {
    override fun onDataChanged(eventos: DataEventBuffer) {
        for (evento in eventos) {
            if (evento.type == DataEvent.TYPE_CHANGED && evento.dataItem.uri.path == CAMINHO_CONFIG) {
                val json = DataMapItem.fromDataItem(evento.dataItem).dataMap.getString("json")
                if (json != null) ConfigStore.guardarJson(this, json)
            }
        }
    }
}
