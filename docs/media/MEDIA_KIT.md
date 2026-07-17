# Unsend — Media Kit (CurseForge / Modrinth)

Imagens de **loja** (página do mod). Não vão dentro do JAR.

Pastas oficiais no projeto:

```
docs/media/store/
  icon/       → ícone do projeto (lista + página)
  header/     → capa / banner (quando a loja permitir)
  gallery/    → prints da galeria da página
docs/media/raw/
  → capturas brutas antes do crop (opcional)
```

Espelho no workspace (opcional):  
`My-Mods-Professional/docs/unsend/media/store/`

---

## Regras de estética (premium)

- UI do Minecraft limpa; **sem** shaders pesados se atrapalharem leitura do chat
- Resolução: **1920×1080** (gallery) / **512×512** (icon)
- Formato: **PNG** (sem compressão feia; sem watermark “MOD BY…”)
- Uma ideia por imagem — sem texto em português e inglês na mesma arte
- Tipografia da loja cuida do marketing; o PNG **mostra o feature**, não um cartaz

---

## 1. Ícone (obrigatório)

| Arquivo | Tamanho | Uso |
|---------|---------|-----|
| `store/icon/unsend-icon-512.png` | **512×512** | Modrinth icon, CurseForge logo, jar `icon.png` fonte |
| `store/icon/unsend-icon-256.png` | 256×256 | fallback / export CF |

**Conteúdo:** símbolo mínimo — envelope / seta de “voltar” / chat bubble com traço de retração.  
Fundo sólido ou suave. Sem “UNSEND” escrito em fonte pixel se não estiver perfeito.

---

## 2. Header / capa (recomendado)

| Arquivo | Tamanho | Uso |
|---------|---------|-----|
| `store/header/unsend-header-1920x400.png` | **1920×400** | banner estilo Modrinth/CF |
| `store/header/unsend-header-1100x260.png` | 1100×260 | export alternativo CF |

**Conteúdo:** nome **Unsend** + uma linha só:  
`Take it back.`  
ou  
`Retract chat messages.`  
Sem lista de features. Sem logos de Forge/Fabric lotando.

---

## 3. Galeria (o que a página precisa)

Ordem sugerida de upload (1 → 5). Tudo em **1920×1080** PNG.

| # | Arquivo | O que mostrar | Caption (EN) | Caption (PT) |
|---|---------|---------------|--------------|--------------|
| 01 | `gallery/01-shift-hover.png` | Chat aberto, **Shift** segurado, mouse na **sua** msg, **lixeira** visível ao lado | Hold Shift over your message | Segure Shift na sua mensagem |
| 02 | `gallery/02-trash-ready.png` | Cursor **em cima da lixeira** (hover highlight sutil) | Click to unsend for everyone | Clique para apagar para todos |
| 03 | `gallery/03-animation.png` | Frame no meio da animação mínima (msg sumindo / letters leves) | Quiet retract animation | Animação discreta de retração |
| 04 | `gallery/04-multiplayer.png` | 2 clients ou split: mensagem some **nos dois** | Removed for every player | Removida para todos os jogadores |
| 05 | `gallery/05-clean-chat.png` | Chat depois — linha sumiu, UI limpa, sem lixo visual | Clean result | Resultado limpo |

### Opcional (só se edit/reply existirem)

| # | Arquivo | O que mostrar |
|---|---------|---------------|
| 06 | `gallery/06-edit.png` | Botão/edit em ação |
| 07 | `gallery/07-reply.png` | Reply com citação |

**Não precisa** de print de “menu de configs” se o mod for zero-config. Menos é mais.

---

## 4. O que **não** colocar na galeria

- Logo sozinho repetido 3 vezes  
- Tela de inventário sem chat  
- Texto enorme “MELHOR MOD DE CHAT 2026”  
- Collage de 6 features numa imagem  
- Print com HUD lotado (minimap, 20 mods de info)

---

## 5. Como capturar (checklist)

1. Mundo limpo ou servidor de teste com **2 contas** (ideal pro print 04)
2. Resolução 1920×1080 no launcher
3. Chat com 3–5 mensagens normais (contexto real)
4. Uma mensagem claramente **sua** no meio/fim
5. Shift + hover → captura 01 e 02
6. Durante unsend → captura 03 (ou GIF separado no futuro; lojas preferem PNG estático na galeria)
7. Depois do unsend → captura 05
8. Crop apenas se sobrar UI feia nas bordas; preferir frame nativo 16:9

---

## 6. Onde cada arquivo sobe

| Destino | O que usar |
|---------|------------|
| **Modrinth** → Icon | `store/icon/unsend-icon-512.png` |
| **Modrinth** → Gallery | `store/gallery/01` … `05` na ordem |
| **Modrinth** → Featured (se pedir) | `01` ou `header` |
| **CurseForge** → Logo | `store/icon/unsend-icon-512.png` |
| **CurseForge** → Cover / Header | `store/header/unsend-header-*.png` |
| **CurseForge** → Screenshots / Gallery | mesma `gallery/` |
| **JAR in-game icon** | copiar o 512 redimensionado → `fabric/.../icon.png` e `forge/.../icon.png` |

Texto da página (description): ver `docs/media/STORE_DESCRIPTION.md`.

---

## 7. Naming final (copiar/colar)

```
docs/media/store/icon/unsend-icon-512.png
docs/media/store/icon/unsend-icon-256.png
docs/media/store/header/unsend-header-1920x400.png
docs/media/store/gallery/01-shift-hover.png
docs/media/store/gallery/02-trash-ready.png
docs/media/store/gallery/03-animation.png
docs/media/store/gallery/04-multiplayer.png
docs/media/store/gallery/05-clean-chat.png
```

Quando as imagens existirem, a descrição da loja referencia só o feature — a **galeria** prova.
