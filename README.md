# WristMind

Assistente de IA por voz para relógios Wear OS. Tocas no ecrã do relógio, falas, e a resposta aparece e é lida em voz alta. A configuração é toda feita numa app no telemóvel Android.

*Voice AI assistant for Wear OS watches, set up from an Android phone. Works with Claude, Gemini or ChatGPT using your own API key.*

![Ecrãs do relógio](docs/relogio.svg)

![App do telemóvel](docs/telemovel.svg)

As imagens são ilustrações do layout, não capturas de ecrã reais.

## O que faz

* Conversa por voz com contexto, com respostas curtas pensadas para o pulso.
* Tradução falada: dizes a frase e o relógio diz a tradução no idioma escolhido.
* Três serviços à escolha: Claude (Anthropic), Gemini (Google) e ChatGPT (OpenAI).
* Nome do assistente à tua escolha.
* Skins: cor dos anéis e cor do fundo do relógio.
* Cada pessoa usa a sua própria chave. As chaves ficam só no telemóvel e no relógio.

## Instalar

Os dois ficheiros estão em [Releases](https://github.com/helteixeira/wristmind/releases/latest).

1. No telemóvel descarrega e instala `WristMindTelemovel.apk`. O Android pede autorização para instalar de fontes desconhecidas.
2. Abre o WristMind, escolhe o serviço de IA, toca em **Obter chave**, cola a chave e toca em **Testar**.
3. Instala `WristMindRelogio.apk` no relógio (ver em baixo).
4. No telemóvel toca em **Guardar e enviar para o relógio**.

### Instalar no relógio

O Android não permite que uma app do telemóvel instale outra app no relógio em silêncio. Enquanto o WristMind não estiver na Play Store, a instalação é feita por depuração sem fios:

1. No relógio abre Definições, Acerca do relógio, e toca 7 vezes no número de compilação.
2. Em Opções de programador liga Depuração ADB e Depuração sem fios.
3. Envia o APK com uma app de instalação para Wear OS a partir do telemóvel, ou a partir de um computador com `adb pair`, `adb connect` e `adb install WristMindRelogio.apk`.

O telemóvel e o relógio têm de ter as duas apps da mesma versão, porque partilham a mesma assinatura.

## Usar

* Toque no ecrã: falar. Toque durante a resposta: interromper e falar outra vez.
* Toque longo: alternar entre Conversa e Tradução.
* Por voz: Ok Google, abre o WristMind.
* Por botão: nas definições do relógio atribui o WristMind a um toque duplo num botão.

## Chaves

| Serviço | Onde criar a chave |
| --- | --- |
| Claude | console.anthropic.com |
| Gemini | aistudio.google.com, com a tua conta Google |
| ChatGPT | platform.openai.com |

As subscrições das apps Claude e ChatGPT não dão acesso a apps de terceiros, por isso é sempre precisa uma chave de API. O custo de utilização é cobrado pelo serviço escolhido na conta de cada pessoa.

## Estado do projeto

Projeto pessoal e experimental, testado em poucos aparelhos. Requer Wear OS 3 ou superior e Android 8 ou superior. Ainda não tem palavra de ativação sempre à escuta nem gravação e resumo de reuniões.

## Compilar

Cada alteração no ramo `main` compila as duas apps com GitHub Actions e publica os APKs em Releases. Para compilar no computador abre a pasta no Android Studio.
