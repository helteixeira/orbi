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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Aplicacao() }
    }
}

private val SEPARADORES = listOf("Assistente", "Aparência", "Relógio", "Ajuda")

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
                        "WristMind",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp),
                    )
                    Text(
                        "O teu assistente de IA no pulso",
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
                                        if (n > 0) "Guardado e enviado para o relógio."
                                        else "Guardado. Não encontrei nenhum relógio ligado."
                                    },
                                    onFailure = { "Guardado no telemóvel, mas falhou o envio para o relógio." },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Guardar e enviar para o relógio") }
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

    Cartao("Quem é o teu assistente") {
        OutlinedTextField(
            value = cfg.nome,
            onValueChange = { mudar(cfg.copy(nome = it)) },
            label = { Text("Nome do assistente") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cfg.utilizador,
            onValueChange = { mudar(cfg.copy(utilizador = it)) },
            label = { Text("O teu nome (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Cartao("Serviço de IA") {
        Escolhas(Fornecedores.todos.map { it.id to it.nome }, cfg.fornecedor) {
            modelos = emptyList()
            mudar(cfg.copy(fornecedor = it))
        }
        Nota(fornecedor.nota)
        OutlinedTextField(
            value = cfg.chaveAtiva(),
            onValueChange = { mudar(cfg.comChave(it.trim())) },
            label = { Text("Chave do " + fornecedor.nome) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { abrirLigacao(ctx, fornecedor.urlChave) }) { Text("Obter chave") }
            OutlinedButton(
                onClick = {
                    val copia = cfg
                    scope.launch {
                        avisar("A testar a ligação...")
                        val r = withContext(Dispatchers.IO) {
                            runCatching { AiClient { copia }.perguntar("Apresenta-te numa frase curta.") }
                        }
                        avisar(r.fold(onSuccess = { it }, onFailure = { "Não consegui ligar. " + (it.message ?: "") }))
                    }
                },
            ) { Text("Testar") }
        }
        OutlinedTextField(
            value = cfg.modeloAtivo(),
            onValueChange = { mudar(cfg.comModelo(it.trim())) },
            label = { Text("Modelo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val copia = cfg
                scope.launch {
                    avisar("A procurar modelos...")
                    val r = withContext(Dispatchers.IO) { runCatching { AiClient { copia }.listarModelos() } }
                    r.onSuccess {
                        modelos = it.take(40)
                        avisar("Encontrei " + it.size + " modelos. Toca num para o escolher.")
                    }.onFailure { avisar("Não consegui listar os modelos. " + (it.message ?: "")) }
                }
            },
        ) { Text("Ver modelos disponíveis") }
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

    Cartao("Voz e idiomas") {
        Nota("Idioma das respostas e da voz")
        Escolhas(Idiomas.todos.map { it.codigo to it.nomeNativo }, cfg.idioma) { mudar(cfg.copy(idioma = it)) }
        Nota("No modo tradução, traduzir para")
        Escolhas(Idiomas.todos.map { it.codigo to it.nomeNativo }, cfg.idiomaTraducao) {
            mudar(cfg.copy(idiomaTraducao = it))
        }
        Interruptor("Responder em voz alta", cfg.voz) { mudar(cfg.copy(voz = it)) }
        Interruptor("Começar a ouvir ao abrir no relógio", cfg.ouvirAoAbrir) { mudar(cfg.copy(ouvirAoAbrir = it)) }
    }
}

@Composable
private fun SeparadorAparencia(cfg: AppConfig, mudar: (AppConfig) -> Unit) {
    Cartao("Skin do relógio") {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PreVisualizacao(cfg.nome, Color(Skins.cor(cfg.skin)), Color(Skins.fundo(cfg.fundo)))
        }
        Nota("Cor dos anéis e das ondas de voz")
        Amostras(Skins.cores, cfg.skin) { mudar(cfg.copy(skin = it)) }
        Nota("Cor do fundo. O preto gasta menos bateria em ecrãs OLED.")
        Amostras(Skins.fundos, cfg.fundo) { mudar(cfg.copy(fundo = it)) }
    }
    Nota("A skin chega ao relógio quando tocas em Guardar e enviar para o relógio.")
}

@Composable
private fun SeparadorRelogio() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var relogios by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        relogios = runCatching { relogiosLigados(ctx) }.getOrDefault(emptyList())
    }

    Cartao("Ligação") {
        val lista = relogios
        Nota(
            when {
                lista == null -> "A procurar relógios..."
                lista.isEmpty() -> "Nenhum relógio Wear OS ligado a este telemóvel."
                else -> "Ligado a " + lista.joinToString(", ")
            },
        )
        OutlinedButton(
            onClick = {
                scope.launch { relogios = runCatching { relogiosLigados(ctx) }.getOrDefault(emptyList()) }
            },
        ) { Text("Procurar outra vez") }
    }

    Cartao("Instalar a app no relógio") {
        Nota(
            "O Android não deixa uma app instalar outra app no relógio em silêncio. " +
                "Enquanto o WristMind não estiver na Play Store, a instalação é feita por depuração sem fios.",
        )
        Passos(
            listOf(
                "Descarrega o ficheiro do relógio com o botão em baixo.",
                "No relógio abre Definições, Acerca do relógio, e toca 7 vezes no número de compilação.",
                "Em Opções de programador liga Depuração ADB e Depuração sem fios.",
                "Envia o ficheiro com uma app de instalação para Wear OS, ou com adb install a partir de um computador.",
                "Abre o WristMind no relógio e volta aqui para enviar a configuração.",
            ),
        )
        Button(onClick = { abrirLigacao(ctx, URL_APK_RELOGIO) }, modifier = Modifier.fillMaxWidth()) {
            Text("Descarregar app do relógio")
        }
    }

    Cartao("Partilhar o WristMind") {
        Nota("Mostra este código a quem quiser instalar. Abre a página com as duas apps.")
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CodigoQr(URL_VERSOES)
        }
        OutlinedButton(onClick = { partilharTexto(ctx, "WristMind, assistente de IA para Wear OS: $URL_VERSOES") }) {
            Text("Partilhar ligação")
        }
    }
}

@Composable
private fun SeparadorAjuda(cfg: AppConfig) {
    val ctx = LocalContext.current

    Cartao("Usar no relógio") {
        Passos(
            listOf(
                "Toca no ecrã e fala. A resposta aparece e é lida em voz alta.",
                "Toca outra vez para interromper e fazer nova pergunta.",
                "Toque longo alterna entre Conversa e Tradução.",
            ),
        )
    }

    Cartao("Chamar o assistente") {
        Passos(
            listOf(
                "Por voz: diz Ok Google, abre o WristMind.",
                "Por botão: nas Definições do relógio procura a personalização dos botões e atribui o WristMind a um toque duplo.",
                "Com Começar a ouvir ao abrir ligado, a app abre já pronta para ouvir " + cfg.nome + ".",
            ),
        )
        Nota("Uma palavra de ativação sempre à escuta ainda não existe, porque gasta muita bateria no relógio.")
    }

    Cartao("Chaves e privacidade") {
        Nota(
            "Cada pessoa usa a sua própria chave. As chaves ficam guardadas só neste telemóvel e no relógio, " +
                "e as perguntas vão diretamente para o serviço que escolheste.",
        )
        Nota("Entrar com a conta Claude ou ChatGPT em vez de chave não é possível em apps de terceiros. No Gemini a chave é criada com a tua conta Google em segundos.")
        OutlinedButton(onClick = { abrirLigacao(ctx, URL_PROJETO) }) { Text("Abrir página do projeto") }
    }
}
