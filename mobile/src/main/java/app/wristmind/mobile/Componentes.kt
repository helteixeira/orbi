package app.wristmind.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wristmind.core.Skin
import app.wristmind.core.tr
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.abs
import kotlin.math.sin

fun abrirLigacao(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun partilharTexto(ctx: Context, texto: String) {
    runCatching {
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        ctx.startActivity(Intent.createChooser(envio, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun Cartao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        conteudo()
    }
}

@Composable
fun Nota(texto: String) {
    Text(texto, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Passos(passos: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        passos.forEachIndexed { i, passo ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((i + 1).toString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    passo,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun Escolhas(itens: List<Pair<String, String>>, escolhido: String, aoEscolher: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itens.forEach { (id, rotulo) ->
            val ativo = id == escolhido
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    )
                    .clickable { aoEscolher(id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    rotulo,
                    fontSize = 14.sp,
                    color = if (ativo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun Interruptor(texto: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(texto, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = ligado, onCheckedChange = aoMudar)
    }
}

@Composable
fun Amostras(itens: List<Skin>, escolhido: String, aoEscolher: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itens.forEach { skin ->
            val ativo = skin.id == escolhido
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(skin.argb))
                    .border(
                        width = if (ativo) 3.dp else 1.dp,
                        color = if (ativo) Color.White else Color(0xFF55606B),
                        shape = CircleShape,
                    )
                    .clickable { aoEscolher(skin.id) },
            )
        }
    }
}

/** Pré visualização do ecrã do relógio com a skin escolhida. */
@Composable
fun PreVisualizacao(nome: String, cor: Color, fundo: Color) {
    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(210.dp)) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            val raio = size.minDimension / 2f

            drawCircle(color = fundo, radius = raio, center = centro)
            drawCircle(color = Color(0xFF3A4652), radius = raio - 2.dp.toPx(), center = centro, style = Stroke(4.dp.toPx()))
            drawCircle(
                color = cor,
                radius = raio * 0.84f,
                center = centro,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx()))),
            )
            drawCircle(
                color = cor,
                radius = raio * 0.75f,
                center = centro,
                alpha = 0.8f,
                style = Stroke(
                    2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(50.dp.toPx(), 12.dp.toPx(), 9.dp.toPx(), 12.dp.toPx()),
                    ),
                ),
            )
            drawCircle(
                color = cor,
                radius = raio * 0.66f,
                center = centro,
                alpha = 0.5f,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )

            val n = 11
            val largura = raio * 0.045f
            val espaco = raio * 0.035f
            val total = n * largura + (n - 1) * espaco
            val inicio = centro.x - total / 2f
            val centroY = centro.y - raio * 0.2f
            for (i in 0 until n) {
                val envelope = 1f - abs(i - (n - 1) / 2f) / (n / 2f + 1f)
                val onda = 0.5f + 0.5f * sin(i * 1.7f)
                val altura = raio * 0.04f + raio * 0.3f * envelope * onda
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(inicio + i * (largura + espaco), centroY - altura / 2f),
                    size = Size(largura, altura),
                    cornerRadius = CornerRadius(largura / 2f, largura / 2f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 70.dp)) {
            Text(nome.ifBlank { "Orbi" }, color = Color(0xFFE6F1FB), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(tr("Tap to talk", "Toca para falar"), color = cor, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun CodigoQr(texto: String) {
    val matriz = remember(texto) {
        runCatching { QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(8.dp),
    ) {
        if (matriz != null) {
            Canvas(modifier = Modifier.size(184.dp)) {
                val n = matriz.width
                val celula = size.width / n
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (matriz.get(x, y)) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * celula, y * celula),
                                size = Size(celula + 0.5f, celula + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
