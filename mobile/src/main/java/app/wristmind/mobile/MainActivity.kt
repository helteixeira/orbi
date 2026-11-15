package app.wristmind.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wristmind.core.AiClient
import app.wristmind.core.AppConfig
import app.wristmind.core.ConfigStore
import app.wristmind.core.Fornecedores
import app.wristmind.core.Idiomas
import app.wristmind.core.Skins
import app.wristmind.core.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Aplicacao() }
    }
}

private val SEPARADORES = listOf(tr("Assistant", "Assistente"), tr("Look", "Aparência"), tr("Watch", "Relógio"), tr("Help", "Ajuda"))

@Composable
private fun Aplicacao() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(ConfigStore.carregar(ctx)) }
    var separador by remember { mutableStateOf(0) }
    var estado by remember { mutableStateOf("") }

    val cores = darkColorScheme(
        primary = Color(Skins.cor(cfg.skin)),
        onPrimary = Color(0xFF06121C),
        background = Color(0xFF0B0F14),
        onBackground = Color(0xFFE6F1FB),
        surface = Color(0xFF0B0F14),
        onSurface = Color(0xFFE6F1FB),
        surfaceVariant = Color(0xFF16202B),
        onSurfaceVariant = Color(0xFFA9B8C6),
    )

    MaterialTheme(colorScheme = cores) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    Text(
                        "Orbi",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp),
                    )
                    Text(
                        tr("Your AI assistant on your wrist", "O teu assistente de IA no pulso"),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                    )
                    TabRow(selectedTabIndex = separador) {
                        SEPARADORES.forEachIndexed { i, titulo ->
                            Tab(
                                selected = i == separador,
                                onClick = { separador = i },
                                text = { Text(titulo, maxLines = 1, fontSize = 13.sp) },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (estado.isNotBlank()) {
                        Text(estado, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val copia = cfg
                            scope.launch {
                                ConfigStore.guardar(ctx, copia)
                                val r = runCatching { enviarParaRelogio(ctx, copia) }
                                estado = r.fold(
                                    onSuccess = { n ->
                                        if (n > 0) tr("Saved and sent to the watch.", "Guardado e enviado para o relógio.")
                                        else tr("Saved. No connected watch found.", "Guardado. Não encontrei nenhum relógio ligado.")
                                    },
                                    onFailure = { tr("Saved on the phone, but sending to the watch failed.", "Guardado no telemóvel, mas falhou o envio para o relógio.") },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Save and send to watch", "Guardar e enviar para o relógio")) }
                }
            },
        ) { margem ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(margem)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (separador) {
                    0 -> SeparadorAssistente(cfg, { cfg = it }, { estado = it })
                    1 -> SeparadorAparencia(cfg) { cfg = it }
                    2 -> SeparadorRelogio()
                    else -> SeparadorAjuda(cfg)
                }
            }
        }
    }
}

@Composable
private fun SeparadorAssistente(cfg: AppConfig, mudar: (AppConfig) -> Unit, avisar: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fornecedor = Fornecedores.porId(cfg.fornecedor)
    var modelos by remember { mutableStateOf<List<String>>(emptyList()) }

    Cartao(tr("Who is your assistant", "Quem é o teu assistente")) {
        OutlinedTextField(
            value = cfg.nome,
            onValueChange = { mudar(cfg.copy(nome = it)) },
            label = { Text(tr("Assistant name", "Nome do assistente")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cfg.utilizador,
            onValueChange = { mudar(cfg.copy(utilizador = it)) },
            label = { Text(tr("Your name (optional)", "O teu nome (opcional)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Cartao(tr("AI service", "Serviço de IA")) {
        Escolhas(Fornecedores.todos.map { it.id to it.nome }, cfg.fornecedor) {
            modelos = emptyList()
            mudar(cfg.copy(fornecedor = it))
        }
        Nota(fornecedor.nota)
        OutlinedTextField(
            value = cfg.chaveAtiva(),
            onValueChange = { mudar(cfg.comChave(it.trim())) },
            label = { Text(tr("Key for ", "Chave do ") + fornecedor.nome) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { abrirLigacao(ctx, fornecedor.urlChave) }) { Text(tr("Get a key", "Obter chave")) }
            OutlinedButton(
                onClick = {
                    val copia = cfg
                    scope.launch {
                        avisar(tr("Testing the connection...", "A testar a ligação..."))
                        val r = withContext(Dispatchers.IO) {
                            runCatching { AiClient { copia }.perguntar(tr("Introduce yourself in one short sentence.", "Apresenta-te numa frase curta.")) }
                        }
                        avisar(r.fold(onSuccess = { it }, onFailure = { tr("Could not connect. ", "Não consegui ligar. ") + (it.message ?: "") }))
                    }
                },
            ) { Text(tr("Test", "Testar")) }
        }
        OutlinedTextField(
            value = cfg.modeloAtivo(),
            onValueChange = { mudar(cfg.comModelo(it.trim())) },
            label = { Text(tr("Model", "Modelo")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val copia = cfg
                scope.launch {
                    avisar(tr("Looking for models...", "A procurar modelos..."))
                    val r = withContext(Dispatchers.IO) { runCatching { AiClient { copia }.listarModelos() } }
                    r.onSuccess {
                        modelos = it.take(40)
                        avisar(tr("Found ", "Encontrei ") + it.size + tr(" models. Tap one to pick it.", " modelos. Toca num para o escolher."))
                    }.onFailure { avisar(tr("Could not list the models. ", "Não consegui listar os modelos. ") + (it.message ?: "")) }
                }
            },
        ) { Text(tr("Show available models", "Ver modelos disponíveis")) }
        modelos.forEach { nome ->
            Text(
                nome,
                fontSize = 14.sp,
                color = if (nome == cfg.modeloAtivo()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mudar(cfg.comModelo(nome)) }
                    .padding(vertical = 8.dp),
            )
        }
    }

    Cartao(tr("Voice and languages", "Voz e idiomas")) {
        Nota(tr("Language for answers and voice", "Idioma das respostas e da voz"))
        Escolhas(Idiomas.todos.map { it.codigo to it.nomeNativo }, cfg.idioma) { mudar(cfg.copy(idioma = it)) }
        Nota(tr("In translation mode, translate to", "No modo tradução, traduzir para"))
        Escolhas(Idiomas.todos.map { it.codigo to it.nomeNativo }, cfg.idiomaTraducao) {
            mudar(cfg.copy(idiomaTraducao = it))
        }
        Interruptor(tr("Speak answers aloud", "Responder em voz alta"), cfg.voz) { mudar(cfg.copy(voz = it)) }
        Interruptor(tr("Start listening when opened on the watch", "Começar a ouvir ao abrir no relógio"), cfg.ouvirAoAbrir) { mudar(cfg.copy(ouvirAoAbrir = it)) }
    }
}

@Composable
private fun SeparadorAparencia(cfg: AppConfig, mudar: (AppConfig) -> Unit) {
    Cartao(tr("Watch skin", "Skin do relógio")) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PreVisualizacao(cfg.nome, Color(Skins.cor(cfg.skin)), Color(Skins.fundo(cfg.fundo)))
        }
        Nota(tr("Colour of the rings and voice waves", "Cor dos anéis e das ondas de voz"))
        Amostras(Skins.cores, cfg.skin) { mudar(cfg.copy(skin = it)) }
        Nota(tr("Background colour. Black uses less battery on OLED screens.", "Cor do fundo. O preto gasta menos bateria em ecrãs OLED."))
        Amostras(Skins.fundos, cfg.fundo) { mudar(cfg.copy(fundo = it)) }
    }
    Nota(tr("The skin reaches the watch when you tap Save and send to watch.", "A skin chega ao relógio quando tocas em Guardar e enviar para o relógio."))
}

@Composable
private fun SeparadorRelogio() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var relogios by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        relogios = runCatching { relogiosLigados(ctx) }.getOrDefault(emptyList())
    }

    Cartao(tr("Connection", "Ligação")) {
        val lista = relogios
        Nota(
            when {
                lista == null -> tr("Looking for watches...", "A procurar relógios...")
                lista.isEmpty() -> tr("No Wear OS watch connected to this phone.", "Nenhum relógio Wear OS ligado a este telemóvel.")
                else -> tr("Connected to ", "Ligado a ") + lista.joinToString(", ")
            },
        )
        OutlinedButton(
            onClick = {
                scope.launch { relogios = runCatching { relogiosLigados(ctx) }.getOrDefault(emptyList()) }
            },
        ) { Text(tr("Search again", "Procurar outra vez")) }
    }

    Cartao(tr("Install the watch app", "Instalar a app no relógio")) {
        Nota(
            tr("Android does not let one app silently install another app on the watch. ", "O Android não deixa uma app instalar outra app no relógio em silêncio. ") +
                tr("Until Orbi is on the Play Store, installation is done over wireless debugging.", "Enquanto o Orbi não estiver na Play Store, a instalação é feita por depuração sem fios."),
        )
        Passos(
            listOf(
                tr("Download the watch file with the button below.", "Descarrega o ficheiro do relógio com o botão em baixo."),
                tr("On the watch open Settings, About watch, and tap the build number 7 times.", "No relógio abre Definições, Acerca do relógio, e toca 7 vezes no número de compilação."),
                tr("In Developer options turn on ADB debugging and Wireless debugging.", "Em Opções de programador liga Depuração ADB e Depuração sem fios."),
                tr("Send the file with a Wear OS installer app, or with adb install from a computer.", "Envia o ficheiro com uma app de instalação para Wear OS, ou com adb install a partir de um computador."),
                tr("Open Orbi on the watch and come back here to send the setup.", "Abre o Orbi no relógio e volta aqui para enviar a configuração."),
            ),
        )
        Button(onClick = { abrirLigacao(ctx, URL_APK_RELOGIO) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Download the watch app", "Descarregar app do relógio"))
        }
    }

    Cartao(tr("Share Orbi", "Partilhar o Orbi")) {
        Nota(tr("Show this code to anyone who wants to install. It opens the page with both apps.", "Mostra este código a quem quiser instalar. Abre a página com as duas apps."))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CodigoQr(URL_VERSOES)
        }
        OutlinedButton(onClick = { partilharTexto(ctx, tr("Orbi, an AI assistant for Wear OS: ", "Orbi, assistente de IA para Wear OS: ") + URL_VERSOES) }) {
            Text(tr("Share link", "Partilhar ligação"))
        }
    }
}

@Composable
private fun SeparadorAjuda(cfg: AppConfig) {
    val ctx = LocalContext.current

    Cartao(tr("Using the watch", "Usar no relógio")) {
        Passos(
            listOf(
                tr("Tap the screen and talk. The answer appears and is read aloud.", "Toca no ecrã e fala. A resposta aparece e é lida em voz alta."),
                tr("Tap again to interrupt and ask something new.", "Toca outra vez para interromper e fazer nova pergunta."),
                tr("Long press switches between Chat and Translation.", "Toque longo alterna entre Conversa e Tradução."),
            ),
        )
    }

    Cartao(tr("Calling the assistant", "Chamar o assistente")) {
        Passos(
            listOf(
                tr("By voice: say Ok Google, open Orbi.", "Por voz: diz Ok Google, abre o Orbi."),
                tr("By button: in the watch Settings find button customisation and assign Orbi to a double press.", "Por botão: nas Definições do relógio procura a personalização dos botões e atribui o Orbi a um toque duplo."),
                tr("With Start listening when opened turned on, the app opens ready to listen as ", "Com Começar a ouvir ao abrir ligado, a app abre já pronta para ouvir ") + cfg.nome + ".",
            ),
        )
        Nota(tr("An always listening wake word is not available yet, because it drains the watch battery.", "Uma palavra de ativação sempre à escuta ainda não existe, porque gasta muita bateria no relógio."))
    }

    Cartao(tr("Keys and privacy", "Chaves e privacidade")) {
        Nota(
            tr("Everyone uses their own key. Keys are stored only on this phone and on the watch, ", "Cada pessoa usa a sua própria chave. As chaves ficam guardadas só neste telemóvel e no relógio, ") +
                tr("and questions go straight to the service you picked.", "e as perguntas vão diretamente para o serviço que escolheste."),
        )
        Nota(tr("Signing in with a Claude or ChatGPT account instead of a key is not possible in third party apps. For Gemini the key is created with your Google account in seconds.", "Entrar com a conta Claude ou ChatGPT em vez de chave não é possível em apps de terceiros. No Gemini a chave é criada com a tua conta Google em segundos."))
        OutlinedButton(onClick = { abrirLigacao(ctx, URL_PROJETO) }) { Text(tr("Open project page", "Abrir página do projeto")) }
    }
}
