# Unsend × Nano Banana — o que pedir

Princípio: **poucas imagens, zero enfeite**.  
IA gera **símbolo e mood**. Texto exato da marca a gente coloca no editor (ou no script), porque modelo costuma errar letra.

---

## O que pedir (lista mínima)

| # | Asset | Ratio | Prioridade |
|---|--------|-------|------------|
| 1 | **Ícone do mod** | 1:1 | Obrigatório |
| 2 | **Header / capa** | 16:9 ou ~5:1 | Obrigatório |
| 3 | **Lixeira UI** (símbolo limpo) | 1:1 | Obrigatório |
| 4 | **Hero still** (opcional) | 16:9 | Só se a loja pedir “featured” |

**Não pedir à IA:** prints do Minecraft com chat legível (faz no jogo).  
**Não pedir:** collage de 6 features, mascote fofo, neon cyberpunk, “logo com 15 efeitos”.

---

## Estilo global (colar no início de todo prompt)

```
Minimal premium product design, flat soft UI, charcoal and cool gray palette,
subtle depth, no clutter, no neon, no cartoon mascot, no busy background,
clean negative space, Apple-like restraint, high clarity, professional software branding.
```

---

## Prompt 1 — Ícone (512×512) — **ESTILO ESCOLHIDO: 2D premium flat**

**Uso:** Modrinth/CurseForge logo + `icon.png` do jar  
**Ratio:** `1:1`  
**Direção:** app icon 2D (bolha + seta de retract + lixeira). Sem Minecraft 3D.

```
App icon for a premium utility product. Soft white chat bubble with a minimal
gray retract/back arrow, tiny refined trash can accent. Rounded square app-icon
shape, deep charcoal background, cool gray highlights. Flat modern 2D icon,
crisp edges, no text, no Minecraft, no voxels, no pixel art. Minimal premium.
```

**Variação B (ainda mais minimal):**
```
Ultra-minimal 2D app icon: white chat bubble on charcoal rounded square,
thin curved undo arrow inside, tiny trash glyph. Flat design, no 3D Minecraft.
```

**Atual no projeto:** `docs/media/store/icon/unsend-icon-512.png` (bolha + seta ← + lixeira).  
Alt: `unsend-icon-alt-512.png`.

---

## Prompt 2 — Header / banner

**Uso:** capa da página  
**Ratio:** `16:9` (depois crop para 1920×400 se precisar)

```
Wide product banner background for a premium chat utility. Dark charcoal
gradient, soft cool gray light from the left, lots of empty space on the left
for title text, small refined chat-bubble + retract symbol on the right third.
No text rendered in the image, no logos of other brands, no Minecraft blocks,
no clutter. Minimal premium software marketing header, cinematic quiet mood.
```

Depois no editor / script:  
**Unsend** + *Take it back.*

---

## Prompt 3 — Lixeira (UI do jogo / símbolo)

**Uso:** base da textura `trash.png` (depois reduzir pra 16×16 pixel-art se quiser)  
**Ratio:** `1:1`

```
Single minimal trash can icon, front view, soft gray metal, closed lid,
clean geometric shapes, transparent background, UI icon for software,
no sparkles, no face, no text, centered, high contrast silhouette,
premium flat design with slight soft shadow.
```

**Tampa aberta (animação):**
```
Same minimal trash can icon as a clean UI asset, lid slightly open angled up,
front view, soft gray metal, transparent background, no text, no sparkles,
matching the closed version style, premium flat design.
```

*(Ideal: gerar a fechada primeiro, e pedir a aberta como **edit** da mesma imagem.)*

---

## Prompt 4 — Hero still (opcional)

**Ratio:** `16:9`

```
Quiet premium product still: dark desk-like abstract surface, one soft glowing
chat message card fading toward a small refined trash icon, motion implied
with subtle blur only on the message, not chaotic. No readable text, no faces,
no Minecraft UI. Minimal professional software marketing image.
```

---

## O que **não** pedir

| Evitar | Por quê |
|--------|---------|
| “Minecraft screenshot with HUD” | IA inventa UI feia |
| “Write UNSEND in cool font” | Letras tortas |
| “Mascote lixeiro fofo” | Quebra tom senior |
| “Neon cyberpunk chat” | Barulho visual |
| “10 features in one image” | Parece mod amador |

---

## Ordem de trabalho com o Banana

1. **Ícone** (1:1) → escolher 1 variante  
2. **Lixeira** fechada → **edit** para aberta (consistência)  
3. **Header** sem texto → compor título em cima  
4. Gallery = **prints reais** do jogo (não Banana)

---

## Checklist de qualidade

- [ ] Funciona em 64×64 (ícone legível)  
- [ ] Poucas cores (carvão + cinza + branco)  
- [ ] Sem texto gerado pela IA  
- [ ] Sem “Minecraft style dirt blocks” no branding  
- [ ] Símbolo entende em 1 segundo: chat + desfazer  

---

## Depois de gerar

Salvar em:

```
docs/media/store/icon/unsend-icon-512.png
docs/media/store/header/unsend-header-1920x400.png
common/src/main/resources/assets/unsend/textures/gui/trash.png
common/src/main/resources/assets/unsend/textures/gui/trash_open.png
fabric|forge/src/main/resources/icon.png
```
