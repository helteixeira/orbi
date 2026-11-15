package app.wristmind.wear

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import app.wristmind.core.AiClient
import app.wristmind.core.ConfigStore
import app.wristmind.core.Idiomas
import app.wristmind.core.AppConfig
import app.wristmind.core.Skins
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

enum class Modo { CONVERSA, TRADUCAO }

sealed interface Estado {
    data object Pronto : Estado
    data object AOuvir : Estado
    data object APensar : Estado
    data class Resposta(val texto: String) : Estado
    data class Erro(val mensagem: String) : Estado
}

private val Verde = Color(0xFF5DCAA5)
private val Roxo = Color(0xFFAFA9EC)
private val Vermelho = Color(0xFFF09595)
private val Texto1 = Color(0xFFE6F1FB)
private val Texto2 = Color(0xFFB5D4F4)

class MainActivity : ComponentActivity() {

    private var config by mutableStateOf(AppConfig())
    private val claude = AiClient { config }
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var estado by mutableStateOf<Estado>(Estado.Pronto)
    private var modo by mutableStateOf(Modo.CONVERSA)

    private val reconhecimento =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            val frase = resultado.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (resultado.resultCode == RESULT_OK && !frase.isNullOrBlank()) {
                perguntar(frase)
            } else {
                estado = Estado.Pronto
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore.carregar(this)

        tts = TextToSpeech(this) { codigo ->
            if (codigo == TextToSpeech.SUCCESS) ttsPronto = true
        }

        setContent {
            val (rotulo, linha) = textos()
            Ecra(
                estado = estado,
                modo = modo,
                destaque = Color(Skins.cor(config.skin)),
                fundo = Color(Skins.fundo(config.fundo)),
                rotulo = rotulo,
                linha = linha,
                aoToque = ::ouvir,
                aoLongo = ::mudarModo,
            )
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                sincronizar()
                delay(300)
                if (config.ouvirAoAbrir && config.chaveAtiva().isNotBlank()) ouvir()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { sincronizar() }
    }

    private suspend fun sincronizar() {
        runCatching {
            val itens = Wearable.getDataClient(this).dataItems.await()
            try {
                for (item in itens) {
                    if (item.uri.path == CAMINHO_CONFIG) {
                        val json = DataMapItem.fromDataItem(item).dataMap.getString("json")
                        if (json != null) ConfigStore.guardarJson(this, json)
                    }
                }
            } finally {
                itens.release()
            }
        }
        config = ConfigStore.carregar(this)
    }

    private fun textos(): Pair<String, String> = when (val e = estado) {
        Estado.Pronto -> config.nome to
            if (modo == Modo.TRADUCAO) {
                getString(R.string.estado_pronto_traducao, Idiomas.nomeEmPortugues(config.idiomaTraducao))
            } else {
                getString(R.string.estado_pronto)
            }
        Estado.AOuvir -> getString(R.string.estado_ouvir) to getString(R.string.dica_ouvir)
        Estado.APensar -> getString(R.string.estado_pensar) to ""
        is Estado.Resposta -> config.nome to e.texto
        is Estado.Erro -> "" to e.mensagem
    }

    private fun ouvir() {
        tts?.stop()
        if (config.chaveAtiva().isBlank()) {
            estado = Estado.Erro(getString(R.string.erro_sem_chave))
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.idioma)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.prompt_voz, config.nome))
        }
        try {
            estado = Estado.AOuvir
            reconhecimento.launch(intent)
        } catch (e: ActivityNotFoundException) {
            estado = Estado.Erro(getString(R.string.erro_sem_voz))
        }
    }

    private fun perguntar(frase: String) {
        estado = Estado.APensar
        val modoAtual = modo
        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    if (modoAtual == Modo.TRADUCAO) claude.traduzir(frase) else claude.perguntar(frase)
                }
            }
            resultado
                .onSuccess { texto ->
                    estado = Estado.Resposta(texto)
                    falar(texto, modoAtual)
                }
                .onFailure { erro ->
                    estado = Estado.Erro(erro.message ?: getString(R.string.erro_generico))
                }
        }
    }

    private fun falar(texto: String, modoAtual: Modo) {
        if (!config.voz || !ttsPronto) return
        val codigo = if (modoAtual == Modo.TRADUCAO) config.idiomaTraducao else config.idioma
        tts?.language = Locale.forLanguageTag(codigo)
        tts?.speak(texto.replace(Regex("[*#_`>]"), ""), TextToSpeech.QUEUE_FLUSH, null, "resposta")
    }

    private fun mudarModo() {
        tts?.stop()
        claude.limpar()
        modo = if (modo == Modo.CONVERSA) Modo.TRADUCAO else Modo.CONVERSA
        estado = Estado.Pronto
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun Ecra(
    estado: Estado,
    modo: Modo,
    destaque: Color,
    fundo: Color,
    rotulo: String,
    linha: String,
    aoToque: () -> Unit,
    aoLongo: () -> Unit,
) {
    val cor = when {
        estado is Estado.Erro -> Vermelho
        estado is Estado.APensar -> Verde
        modo == Modo.TRADUCAO -> Roxo
        else -> destaque
    }
    val amplitude = when (estado) {
        Estado.Pronto -> 0.12f
        Estado.AOuvir -> 0.95f
        Estado.APensar -> 0.2f
        is Estado.Resposta -> 0.55f
        is Estado.Erro -> 0.05f
    }
    val velocidade = when (estado) {
        Estado.Pronto -> 0.5f
        Estado.AOuvir -> 1.4f
        Estado.APensar -> 2.4f
        is Estado.Resposta -> 1f
        is Estado.Erro -> 0.3f
    }

    val toque by rememberUpdatedState(aoToque)
    val longo by rememberUpdatedState(aoLongo)

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fundo)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { toque() }, onLongPress = { longo() })
                    },
            ) {
                Hud(cor = cor, amplitude = amplitude, velocidade = velocidade, modifier = Modifier.fillMaxSize())

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(0.52f))
                    Column(
                        modifier = Modifier
                            .weight(0.34f)
                            .padding(horizontal = 26.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (rotulo.isNotBlank()) {
                            Text(
                                text = rotulo,
                                color = Texto1,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (linha.isNotBlank()) {
                            Text(
                                text = linha,
                                color = Texto2,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.weight(0.14f))
                }
            }
        }
    }
}

@Composable
private fun Hud(cor: Color, amplitude: Float, velocidade: Float, modifier: Modifier = Modifier) {
    var angulo by remember { mutableFloatStateOf(0f) }
    var fase by remember { mutableFloatStateOf(0f) }
    val vel by rememberUpdatedState(velocidade)

    LaunchedEffect(Unit) {
        var ultimo = 0L
        while (true) {
            withFrameMillis { agora ->
                if (ultimo != 0L) {
                    val dt = (agora - ultimo) / 1000f
                    angulo = (angulo + dt * 14f * vel) % 360f
                    fase += dt * 7f * vel
                }
                ultimo = agora
            }
        }
    }

    Canvas(modifier = modifier) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val raio = size.minDimension / 2f

        rotate(degrees = angulo, pivot = centro) {
            drawCircle(
                color = cor,
                radius = raio * 0.86f,
                center = centro,
                alpha = 0.9f,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 9.dp.toPx())),
                ),
            )
        }
        rotate(degrees = -angulo * 0.6f, pivot = centro) {
            drawCircle(
                color = cor,
                radius = raio * 0.77f,
                center = centro,
                alpha = 0.8f,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(60.dp.toPx(), 14.dp.toPx(), 10.dp.toPx(), 14.dp.toPx()),
                    ),
                ),
            )
        }
        rotate(degrees = angulo * 0.3f, pivot = centro) {
            drawCircle(
                color = cor,
                radius = raio * 0.68f,
                center = centro,
                alpha = 0.5f,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }

        val n = 11
        val largura = raio * 0.045f
        val espaco = raio * 0.035f
        val total = n * largura + (n - 1) * espaco
        val inicio = centro.x - total / 2f
        val centroY = centro.y - raio * 0.36f
        for (i in 0 until n) {
            val envelope = 1f - abs(i - (n - 1) / 2f) / (n / 2f + 1f)
            val onda = 0.5f + 0.5f * sin(fase + i * 0.8f)
            val altura = raio * 0.03f + amplitude * raio * 0.3f * envelope * onda
            drawRoundRect(
                color = cor,
                topLeft = Offset(inicio + i * (largura + espaco), centroY - altura / 2f),
                size = Size(largura, altura),
                cornerRadius = CornerRadius(largura / 2f, largura / 2f),
            )
        }
    }
}
