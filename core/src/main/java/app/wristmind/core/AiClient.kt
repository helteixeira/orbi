package app.wristmind.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de conversa para Claude, Gemini e ChatGPT.
 * Cada pessoa usa a sua própria chave. O histórico fica só em memória.
 */
class AiClient(private val config: () -> AppConfig) {

    private data class Mensagem(val doUtilizador: Boolean, val texto: String)

    private val historico = ArrayList<Mensagem>()

    @Synchronized
    fun perguntar(frase: String): String {
        val c = config()
        val mensagens = historico + Mensagem(true, frase)
        val texto = chamar(c, Prompts.conversa(c), mensagens, 600)
        historico.add(Mensagem(true, frase))
        historico.add(Mensagem(false, texto))
        while (historico.size > 20) {
            historico.removeAt(0)
            historico.removeAt(0)
        }
        return texto
    }

    fun traduzir(frase: String): String {
        val c = config()
        return chamar(c, Prompts.traducao(c), listOf(Mensagem(true, frase)), 500)
    }

    @Synchronized
    fun limpar() = historico.clear()

    /** Lista os modelos que a chave atual pode usar. */
    fun listarModelos(): List<String> {
        val c = config()
        exigirChave(c)
        val chave = c.chaveAtiva()
        val nomes = ArrayList<String>()
        when (c.fornecedor) {
            Fornecedores.GEMINI.id -> {
                val r = pedido("GET", "$GEMINI/models?pageSize=200", mapOf("x-goog-api-key" to chave), null)
                val lista = JSONObject(r).optJSONArray("models") ?: JSONArray()
                for (i in 0 until lista.length()) {
                    val m = lista.getJSONObject(i)
                    val metodos = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                    if (metodos.contains("generateContent")) {
                        nomes.add(m.optString("name").removePrefix("models/"))
                    }
                }
            }
            Fornecedores.OPENAI.id -> {
                val r = pedido("GET", "$OPENAI/models", mapOf("Authorization" to "Bearer $chave"), null)
                val lista = JSONObject(r).optJSONArray("data") ?: JSONArray()
                for (i in 0 until lista.length()) {
                    val id = lista.getJSONObject(i).optString("id")
                    if (id.startsWith("gpt") || id.startsWith("chat") || id.startsWith("o")) nomes.add(id)
                }
            }
            else -> {
                val r = pedido("GET", "$ANTHROPIC/models?limit=100", cabecalhosAnthropic(chave), null)
                val lista = JSONObject(r).optJSONArray("data") ?: JSONArray()
                for (i in 0 until lista.length()) nomes.add(lista.getJSONObject(i).optString("id"))
            }
        }
        return nomes.filter { it.isNotBlank() }.distinct().sortedDescending()
    }

    private fun chamar(c: AppConfig, sistema: String, mensagens: List<Mensagem>, maxTokens: Int): String {
        exigirChave(c)
        val chave = c.chaveAtiva()
        val modelo = c.modeloAtivo()
        val texto = when (c.fornecedor) {
            Fornecedores.GEMINI.id -> gemini(chave, modelo, sistema, mensagens, maxTokens)
            Fornecedores.OPENAI.id -> openAi(chave, modelo, sistema, mensagens, maxTokens)
            else -> anthropic(chave, modelo, sistema, mensagens, maxTokens)
        }.trim()
        if (texto.isEmpty()) throw IOException(tr("Empty answer. Try another model.", "Resposta vazia. Experimenta outro modelo."))
        return texto
    }

    private fun exigirChave(c: AppConfig) {
        if (c.chaveAtiva().isBlank()) {
            throw IOException(tr("Missing key for ", "Falta a chave do ") + Fornecedores.porId(c.fornecedor).nome + tr(". Finish the setup on the phone.", ". Faz o setup no telemóvel."))
        }
    }

    private fun cabecalhosAnthropic(chave: String) =
        mapOf("x-api-key" to chave, "anthropic-version" to "2023-06-01")

    private fun anthropic(chave: String, modelo: String, sistema: String, msgs: List<Mensagem>, max: Int): String {
        val lista = JSONArray()
        for (m in msgs) {
            lista.put(JSONObject().put("role", if (m.doUtilizador) "user" else "assistant").put("content", m.texto))
        }
        val corpo = JSONObject()
            .put("model", modelo)
            .put("max_tokens", max)
            .put("system", sistema)
            .put("messages", lista)
        val r = JSONObject(pedido("POST", "$ANTHROPIC/messages", cabecalhosAnthropic(chave), corpo.toString()))
        val blocos = r.optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until blocos.length()) {
            val b = blocos.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return sb.toString()
    }

    private fun gemini(chave: String, modelo: String, sistema: String, msgs: List<Mensagem>, max: Int): String {
        val lista = JSONArray()
        for (m in msgs) {
            lista.put(
                JSONObject()
                    .put("role", if (m.doUtilizador) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.texto))),
            )
        }
        val corpo = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistema))))
            .put("contents", lista)
            .put("generationConfig", JSONObject().put("maxOutputTokens", max + 1500))
        val r = JSONObject(
            pedido("POST", "$GEMINI/models/$modelo:generateContent", mapOf("x-goog-api-key" to chave), corpo.toString()),
        )
        val partes = r.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until partes.length()) {
            val p = partes.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    private fun openAi(chave: String, modelo: String, sistema: String, msgs: List<Mensagem>, max: Int): String {
        val lista = JSONArray().put(JSONObject().put("role", "system").put("content", sistema))
        for (m in msgs) {
            lista.put(JSONObject().put("role", if (m.doUtilizador) "user" else "assistant").put("content", m.texto))
        }
        val corpo = JSONObject()
            .put("model", modelo)
            .put("messages", lista)
            .put("max_completion_tokens", max + 1500)
        val r = JSONObject(
            pedido("POST", "$OPENAI/chat/completions", mapOf("Authorization" to "Bearer $chave"), corpo.toString()),
        )
        return r.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "") ?: ""
    }

    private fun pedido(metodo: String, endereco: String, cabecalhos: Map<String, String>, corpo: String?): String {
        val ligacao = (URL(endereco).openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("content-type", "application/json")
            for ((k, v) in cabecalhos) setRequestProperty(k, v)
        }
        try {
            if (corpo != null) {
                ligacao.doOutput = true
                ligacao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = ligacao.responseCode
            val fluxo = if (codigo in 200..299) ligacao.inputStream else ligacao.errorStream
            val resposta = fluxo?.bufferedReader()?.use { it.readText() } ?: ""
            if (codigo !in 200..299) throw IOException(mensagemDeErro(codigo, resposta))
            return resposta
        } finally {
            ligacao.disconnect()
        }
    }

    private fun mensagemDeErro(codigo: Int, corpo: String): String {
        val detalhe = runCatching {
            JSONObject(corpo).getJSONObject("error").getString("message")
        }.getOrDefault("").take(140)
        val base = when (codigo) {
            400 -> tr("Request rejected. Check the key and the model.", "Pedido recusado. Confirma a chave e o modelo.")
            401 -> tr("The key is not valid.", "A chave não é válida.")
            403 -> tr("The key is not allowed to make this request.", "A chave não tem permissão para este pedido.")
            404 -> tr("Model not found. Pick another one on the phone.", "Modelo não encontrado. Escolhe outro no telemóvel.")
            429 -> tr("Too many requests or no credit on the account.", "Demasiados pedidos ou sem saldo na conta.")
            in 500..599 -> tr("The service is having problems. Try again shortly.", "O serviço está com problemas. Tenta daqui a pouco.")
            else -> tr("Error ", "Erro ") + codigo + "."
        }
        return if (detalhe.isBlank()) base else "$base $detalhe"
    }

    companion object {
        private const val ANTHROPIC = "https://api.anthropic.com/v1"
        private const val GEMINI = "https://generativelanguage.googleapis.com/v1beta"
        private const val OPENAI = "https://api.openai.com/v1"
    }
}

object Prompts {

    fun conversa(c: AppConfig): String = buildString {
        append("Your name is ")
        append(c.nome)
        append(". You are the personal assistant")
        if (c.utilizador.isNotBlank()) {
            append(" of ")
            append(c.utilizador)
        }
        append(", who talks to you by voice through a smartwatch. ")
        append("Always answer in ")
        append(Idiomas.nomeEmIngles(c.idioma))
        append(", in a natural, warm and efficient tone. ")
        append("Answers are read aloud on a small screen, so use one to three short sentences. ")
        append("Do not use markdown, lists, symbols, emojis or dashes. ")
        append("If the question needs a long answer, give the essentials and offer to continue.")
    }

    fun traducao(c: AppConfig): String =
        "You are a translator. Translate the user's text into " + Idiomas.nomeEmIngles(c.idiomaTraducao) +
            ". The text may be in any language. Return only the translation, with no quotes, notes or comments."
}
