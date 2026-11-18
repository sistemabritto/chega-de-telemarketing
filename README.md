# 📵 Chega de Telemarketing

App Android **grátis e de código aberto** que recusa sozinho as ligações de telemarketing e call center — **sem modo avião** e sem perder os dados móveis. A ligação nem chega a tocar.

> Feito para o Brasil, cansado de receber 50 ligações por dia de call center.

## ⬇️ Baixar

👉 **[Baixar o APK mais recente](../../releases/latest)**

Funciona em Android 10 ou mais novo (testado pensando no Samsung Galaxy A15 / One UI).

## O que ele bloqueia

| Regra | Padrão |
|---|---|
| Números **0303** (telemarketing oficial da Anatel) e **0304** (doações) | ligado |
| Números **ocultos / privados** | ligado |
| **Todos que não estão nos seus contatos** (o modo "acabou a paz deles") | desligado |
| Prefixos que você escolher (ex.: `0800`, `11 4003`) | vazio |
| Desconhecido que liga **2x em 5 minutos** passa (pode ser urgente: hospital, entrega) | ligado |

- ✅ Quem está nos seus **contatos nunca é bloqueado**.
- ✅ Histórico de tudo que foi bloqueado, com data, número e motivo.
- 🔒 **Sem permissão de internet**: nada sai do seu celular. Pode conferir no código.

## Como instalar

1. Baixe o arquivo `.apk` na página de [Releases](../../releases/latest) pelo celular.
2. Toque no arquivo. O Android vai pedir para **permitir instalar apps desta fonte** (Chrome ou "Meus Arquivos") — permita.
   - Se o Play Protect avisar, toque em **Mais detalhes → Instalar mesmo assim** (acontece com todo app fora da Play Store).
3. Abra o app e toque em **Ativar proteção**.
4. Escolha **Chega de Telemarketing** como **app de ID de chamadas e spam**.

### Samsung (Galaxy A15 e outros)

Se o botão Ativar não abrir nada, vá em:
**Configurações → Aplicativos → Escolher apps padrão → App de ID de chamadas e spam → Chega de Telemarketing**
(o botão "Abrir apps padrão do Android" no app leva direto para lá).

Para bloquear **todo mundo fora dos contatos**, ligue a chave e permita o acesso aos contatos.

## Dicas extras (grátis)

- Cadastre seu número no **[Não Me Perturbe](https://www.naomeperturbe.com.br)** — cadastro oficial das operadoras e bancos para não receber oferta por telefone.
- Reclame na **Anatel** (app *Anatel Consumidor* ou 1331) quando uma empresa insistir.

## Como funciona (técnico)

Usa a API oficial do Android [`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService). O sistema pergunta ao app, antes de tocar, se a ligação deve passar; se não, ela é recusada em silêncio. A rede móvel continua funcionando normalmente.

```
app/src/main/java/br/com/chegadetelemarketing/
├── BlockerService.kt   # recebe cada ligação e responde: passa ou recusa
├── Rules.kt            # regras de bloqueio
├── Settings.kt         # configurações e histórico (só no aparelho)
└── MainActivity.kt     # tela
```

### Compilar

O APK é compilado automaticamente pelo GitHub Actions a cada versão (`git tag vX.Y.Z`). Para compilar na sua máquina: JDK 17 + Android SDK, depois `gradle assembleRelease`.

## Contribua

Achou um padrão de número de call center que deveria ser bloqueado? Abra uma *issue* ou um *pull request*. 🇧🇷

## Licença

[MIT](LICENSE) — use, copie e melhore à vontade.
