# Meu IPTV

App Android pessoal: um launcher com abas em WebView e um player de listas
M3U/Xtream Codes, para você organizar os **seus próprios** serviços de
IPTV/streaming num só lugar no celular ou na TV.

## O que este app é (e o que ele não é)

- **Sem conta central.** Não existe login, senha de acesso, captcha nem
  servidor próprio que decida quem pode usar o app. Cada pessoa que instala
  cadastra, neste próprio aparelho, os serviços que ela já tem acesso.
- **Sem domínio fixo.** O app não aponta para nenhum site específico. Você
  informa a URL do painel, o link M3U ou os dados Xtream Codes de cada
  serviço seu.
- **Sem bloqueio remoto.** Não há checagem de assinatura de APK, nem
  verificação de versão obrigatória, nem "kill switch" controlado por
  terceiros.
- **Senhas, se você quiser salvar, ficam só no aparelho**, cifradas com
  AES-256-GCM usando o Android Keystore (a chave nunca sai do hardware
  seguro do celular). Você decide quando salvar e quando esquecer.

Este projeto foi construído como uma reimplementação própria, inspirada na
arquitetura técnica (launcher + abas WebView + player M3U) observada durante
uma análise de engenharia reversa de um app de terceiros — sem reaproveitar
código, marca, domínios ou contas desse app original.

## Estrutura do projeto

```
MeuIPTV/
├── app/
│   └── src/main/
│       ├── java/br/com/whitney/meuiptv/
│       │   ├── MainActivity.kt           # tela inicial com os serviços salvos
│       │   ├── AddServiceActivity.kt     # cadastro de painel / lista M3U / Xtream
│       │   ├── WebPanelActivity.kt       # WebView em tela cheia para painéis
│       │   ├── ChannelListActivity.kt    # lista de canais de uma playlist
│       │   ├── PlayerActivity.kt         # player (Media3/ExoPlayer)
│       │   ├── Service.kt                # modelo + armazenamento local dos serviços
│       │   ├── PasswordVault.kt          # cofre de senhas local (AES-GCM/Keystore)
│       │   ├── PlaylistLoader.kt         # parser M3U + cliente Xtream Codes
│       │   └── Channel.kt
│       ├── res/                          # layouts, cores, textos, ícone
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Como gerar o APK

Este ambiente de geração não tem acesso ao Android SDK nem ao Gradle
oficial (rede restrita), então o projeto foi criado como código-fonte puro,
pronto para abrir no **Android Studio**:

1. Instale o [Android Studio](https://developer.android.com/studio) (ele já
   traz o Gradle e o Android SDK necessários).
2. Abra o Android Studio → **Open** → selecione a pasta `MeuIPTV`.
3. Deixe o Studio sincronizar o projeto (ele baixa as dependências listadas
   em `app/build.gradle.kts` automaticamente).
4. Rode **Build → Generate Signed Bundle / APK** para gerar o `.apk`, ou use
   o botão ▶ para instalar direto num aparelho/emulador conectado.

Se preferir linha de comando: depois de abrir uma vez no Android Studio (ele
cria o `gradlew`/wrapper automaticamente), rode:

```bash
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Como usar

1. Abra o app e toque no botão **+**.
2. Escolha o tipo de serviço:
   - **Painel** — informe a URL do site/painel (abre numa aba dedicada,
     como um navegador).
   - **Lista de canais** — informe um link M3U, ou os dados Xtream Codes
     (servidor, usuário, senha) do seu provedor.
3. Toque no card do serviço para abri-lo.
4. Num painel, o menu (⋮) permite salvar o login daquele serviço neste
   aparelho — opcional, e sempre feito a seu pedido.

## Permissões usadas

- `INTERNET` e `ACCESS_NETWORK_STATE` — para abrir os sites e carregar as
  listas de canais que você cadastrar. Nada além disso.

## Próximos passos possíveis

- Favoritos e categorias de canais.
- Suporte a EPG (guia de programação) quando a lista M3U trouxer essa
  informação.
- Exportar/importar a lista de serviços cadastrados (backup local).
