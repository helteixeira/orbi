package app.wristmind.core

import android.content.Context
import org.json.JSONObject

/** Um serviço de IA suportado. Cada pessoa usa a sua própria chave. */
data class Fornecedor(
    val id: String,
    val nome: String,
    val urlChave: String,
    val modeloPadrao: String,
    val nota: String,
)

object Fornecedores {
    val ANTHROPIC = Fornecedor(
        id = "anthropic",
        nome = "Claude",
        urlChave = "https://console.anthropic.com/settings/keys",
        modeloPadrao = "claude-haiku-5-5",
        nota = tr("Paid key from your Anthropic API account. A Claude app subscription does not work here.", "Chave paga pela conta da API da Anthropic. A subscrição da app Claude não serve."),
    )
    val GEMINI = Fornecedor(
        id = "gemini",
        nome = "Gemini",
        urlChave = "https://aistudio.google.com/apikey",
        modeloPadrao = "gemini-3.8-flash",
        nota = tr("Sign in to AI Studio with your Google account and create a key in seconds.", "Entras com a tua conta Google no AI Studio e crias a chave em segundos."),
    )
    val OPENAI = Fornecedor(
        id = "openai",
        nome = "ChatGPT",
        urlChave = "https://platform.openai.com/api-keys",
        modeloPadrao = "chat-latest",
        nota = tr("Paid key from your OpenAI API account. A ChatGPT subscription does not work here.", "Chave paga pela conta da API da OpenAI. A subscrição do ChatGPT não serve."),
    )

    val todos = listOf(ANTHROPIC, GEMINI, OPENAI)

    fun porId(id: String): Fornecedor = todos.firstOrNull { it.id == id } ?: ANTHROPIC
}

data class Skin(val id: String, val nome: String, val argb: Long)

object Skins {
    val cores = listOf(
        Skin("azul", "Azul", 0xFF85B7EB),
        Skin("ciano", "Ciano", 0xFF4DD8E6),
        Skin("verde", "Verde", 0xFF5DCAA5),
        Skin("ambar", "Âmbar", 0xFFF2B84B),
        Skin("vermelho", "Vermelho", 0xFFF07A7A),
        Skin("roxo", "Roxo", 0xFFAFA9EC),
        Skin("branco", "Branco", 0xFFE8EEF4),
    )
    val fundos = listOf(
        Skin("preto", "Preto", 0xFF000000),
        Skin("grafite", "Grafite", 0xFF0B0F14),
        Skin("noite", "Azul noite", 0xFF0A1428),
        Skin("ameixa", "Ameixa", 0xFF170C26),
        Skin("floresta", "Floresta", 0xFF07180F),
    )

    fun cor(id: String): Long = (cores.firstOrNull { it.id == id } ?: cores.first()).argb
    fun fundo(id: String): Long = (fundos.firstOrNull { it.id == id } ?: fundos.first()).argb
}

data class Idioma(val codigo: String, val nomeNativo: String, val nomeEn: String, val nomePt: String)

object Idiomas {
    val todos = listOf(
        Idioma("pt-PT", "Português (Portugal)", "European Portuguese", "português europeu"),
        Idioma("en-US", "English", "English", "inglês"),
        Idioma("es-ES", "Español", "Spanish", "espanhol"),
        Idioma("de-DE", "Deutsch", "German", "alemão"),
        Idioma("fr-FR", "Français", "French", "francês"),
    )

    private fun procurar(codigo: String): Idioma = todos.firstOrNull { it.codigo == codigo } ?: todos[1]

    /** Language name in English, used in the instructions sent to the model. */
    fun nomeEmIngles(codigo: String): String = procurar(codigo).nomeEn

    /** Language name shown to the user, in the device language. */
    fun nome(codigo: String): String = procurar(codigo).let { tr(it.nomeEn, it.nomePt) }
}

data class AppConfig(
    val nome: String = "Orbi",
    val utilizador: String = "",
    val fornecedor: String = Fornecedores.ANTHROPIC.id,
    val chaveAnthropic: String = "",
    val chaveGemini: String = "",
    val chaveOpenAi: String = "",
    val modeloAnthropic: String = Fornecedores.ANTHROPIC.modeloPadrao,
    val modeloGemini: String = Fornecedores.GEMINI.modeloPadrao,
    val modeloOpenAi: String = Fornecedores.OPENAI.modeloPadrao,
    val idioma: String = if (devicePrefersPortuguese()) "pt-PT" else "en-US",
    val idiomaTraducao: String = if (devicePrefersPortuguese()) "en-US" else "es-ES",
    val voz: Boolean = true,
    val ouvirAoAbrir: Boolean = true,
    val skin: String = "azul",
    val fundo: String = "preto",
) {
    fun chaveAtiva(): String = when (fornecedor) {
        Fornecedores.GEMINI.id -> chaveGemini
        Fornecedores.OPENAI.id -> chaveOpenAi
        else -> chaveAnthropic
    }.trim()

    fun modeloAtivo(): String = when (fornecedor) {
        Fornecedores.GEMINI.id -> modeloGemini
        Fornecedores.OPENAI.id -> modeloOpenAi
        else -> modeloAnthropic
    }.trim().ifBlank { Fornecedores.porId(fornecedor).modeloPadrao }

    fun comChave(valor: String): AppConfig = when (fornecedor) {
        Fornecedores.GEMINI.id -> copy(chaveGemini = valor)
        Fornecedores.OPENAI.id -> copy(chaveOpenAi = valor)
        else -> copy(chaveAnthropic = valor)
    }

    fun comModelo(valor: String): AppConfig = when (fornecedor) {
        Fornecedores.GEMINI.id -> copy(modeloGemini = valor)
        Fornecedores.OPENAI.id -> copy(modeloOpenAi = valor)
        else -> copy(modeloAnthropic = valor)
    }
}

object ConfigStore {
    private const val FICHEIRO = "wristmind_config"
    private const val CHAVE = "json"

    fun carregar(ctx: Context): AppConfig =
        deJson(ctx.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE).getString(CHAVE, null))

    fun guardar(ctx: Context, config: AppConfig) = guardarJson(ctx, paraJson(config))

    fun guardarJson(ctx: Context, json: String) {
        ctx.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)
            .edit()
            .putString(CHAVE, json)
            .apply()
    }

    fun paraJson(c: AppConfig): String = JSONObject()
        .put("nome", c.nome)
        .put("utilizador", c.utilizador)
        .put("fornecedor", c.fornecedor)
        .put("chaveAnthropic", c.chaveAnthropic)
        .put("chaveGemini", c.chaveGemini)
        .put("chaveOpenAi", c.chaveOpenAi)
        .put("modeloAnthropic", c.modeloAnthropic)
        .put("modeloGemini", c.modeloGemini)
        .put("modeloOpenAi", c.modeloOpenAi)
        .put("idioma", c.idioma)
        .put("idiomaTraducao", c.idiomaTraducao)
        .put("voz", c.voz)
        .put("ouvirAoAbrir", c.ouvirAoAbrir)
        .put("skin", c.skin)
        .put("fundo", c.fundo)
        .toString()

    fun deJson(texto: String?): AppConfig {
        if (texto.isNullOrBlank()) return AppConfig()
        return runCatching {
            val o = JSONObject(texto)
            val b = AppConfig()
            AppConfig(
                nome = o.optString("nome", b.nome).ifBlank { b.nome },
                utilizador = o.optString("utilizador", ""),
                fornecedor = o.optString("fornecedor", b.fornecedor).ifBlank { b.fornecedor },
                chaveAnthropic = o.optString("chaveAnthropic", ""),
                chaveGemini = o.optString("chaveGemini", ""),
                chaveOpenAi = o.optString("chaveOpenAi", ""),
                modeloAnthropic = o.optString("modeloAnthropic", b.modeloAnthropic),
                modeloGemini = o.optString("modeloGemini", b.modeloGemini),
                modeloOpenAi = o.optString("modeloOpenAi", b.modeloOpenAi),
                idioma = o.optString("idioma", b.idioma).ifBlank { b.idioma },
                idiomaTraducao = o.optString("idiomaTraducao", b.idiomaTraducao).ifBlank { b.idiomaTraducao },
                voz = o.optBoolean("voz", b.voz),
                ouvirAoAbrir = o.optBoolean("ouvirAoAbrir", b.ouvirAoAbrir),
                skin = o.optString("skin", b.skin).ifBlank { b.skin },
                fundo = o.optString("fundo", b.fundo).ifBlank { b.fundo },
            )
        }.getOrDefault(AppConfig())
    }
}
