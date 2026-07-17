# Unsend — UX Spec (Delete · Edit · Reply)

Tom: direto. Ícones premium. Chat global.

---

## 1. Delete

| Quem | O quê |
|------|--------|
| **Você** | Apaga **só suas** mensagens **para todos** |
| **Admin / OP** (nível 2+) | Pode apagar mensagem **de outros** para todos |
| Demais | Sem botão de lixeira em msg alheia |

### Como

- Chat aberto → **Shift** → hover na mensagem  
- **Lixeira** (ícone premium) ao lado da msg  
- Clique → animação → some para todos  

**Atalhos**

| Atalho | Ação |
|--------|------|
| Clique na lixeira | Delete |
| **Shift + Delete** (com hover na msg) | Delete (mesmas regras de permissão) |

---

## 2. Edit

| Quem | O quê |
|------|--------|
| **Só o autor** | Edita a **mesma** linha para todos |
| Outros / OP | **Não** edita msg alheia (só delete se OP) |

### Como

- Shift + hover na **sua** msg → ícone **lápis**  
- Ou: input do chat **vazio** + **↑** → edita **última mensagem sua** (estilo Discord) — **confirmado**  
- Campo preenche com o texto atual  
- Enter → atualiza a linha + badge **`(editado)`**  
- **Esc** → cancela modo edição  

### Regras

- Não cria mensagem nova  
- Limite de tamanho vanilla (256)  
- Vazio = bloqueado  
- Só se a msg ainda existir no tracker  

---

## 3. Reply

| Quem | O quê |
|------|--------|
| **Qualquer jogador** | Pode responder qualquer mensagem rastreada |

### Visual (definido)

```
  ↗                                    ← seta no canto superior direito da linha
<Nome> texto da mensagem original…
```

- **Seta** premium, canto **superior direito** da mensagem (fim direito, em cima)  
- **Não** exige Shift para aparecer? → ver §4  

### Fluxo

1. Clique na seta de reply  
2. Input do chat entra em modo reply (citação)  
3. Enter → manda **no chat global** (normal), com citação visual  
4. **Esc** ou limpar → cancela reply  

### Formato da citação (HUD / chat)

```
  ↳ Nome · trecho original…
<Você> sua resposta
```

Se a original foi apagada:

```
  ↳ [mensagem apagada]
<Você> sua resposta
```

### Atalhos

| Atalho | Ação |
|--------|------|
| Clique na seta ↗ | Reply |
| (Opcional v1.1) Shift+clique na linha | Reply |

---

## 4. Quando cada ícone aparece

| Ícone | Posição | Visível quando |
|-------|---------|----------------|
| **Reply ↗** | Canto **superior direito** da linha | Hover na mensagem (chat aberto) — sempre que a msg for rastreada |
| **Edit ✎** | Ao lado / barra de ações | **Shift** + hover + **é sua** |
| **Delete 🗑** | Ao lado / barra de ações | **Shift** + hover + (**é sua** **ou** você é OP) |

**Por quê Reply sem Shift:** responder é o mais comum; a setinha discreta no canto não polui como lixeira.  
**Edit/Delete com Shift:** ações destrutivas / de dono ficam “atrás” de um gesto consciente.

```
┌─────────────────────────────────────────────┐
│ <Steve> eae galera                     [↗]  │  ← hover: só reply
└─────────────────────────────────────────────┘

Shift + hover na SUA msg:
┌─────────────────────────────────────────────┐
│ <Você> mandei errado          [✎] [🗑] [↗]  │
└─────────────────────────────────────────────┘

Shift + hover em msg de outro (você OP):
┌─────────────────────────────────────────────┐
│ <Steve> spam                      [🗑] [↗]  │
└─────────────────────────────────────────────┘
```

---

## 5. Atalhos — tabela final

| Ação | Mouse | Teclado |
|------|--------|---------|
| **Reply** | Clique na seta ↗ (topo-direita) | — (v1) |
| **Edit** | Shift + lápis | **↑** no input **vazio** = editar última sua |
| **Delete** | Shift + lixeira | **Shift + Delete** com hover |
| **Cancelar** edit/reply | — | **Esc** |

---

## 6. Rede / chat global

- Delete, edit e reply passam pelo **servidor**  
- Reply usa o **mesmo chat global** vanilla (não canal walkie separado)  
- Cliente + servidor com o mod  

### Packets (esboço)

| Packet | Direção | Payload |
|--------|---------|---------|
| `Register` | S2C | id, sender, text |
| `Delete` | C2S / S2C | id |
| `Edit` | C2S | id, newText |
| `Edit` | S2C | id, newText |
| `Reply` | embutido no chat + metadata S2C `replyToId` + preview | |

Permissão delete no server:

```
podeApagar = msg.sender == player || player.hasPermissions(2)
```

---

## 7. Ícones premium

| Ícone | Estilo |
|-------|--------|
| Reply | Seta curva / “corner up-right” fina, cinza claro |
| Edit | Lápis mínimo |
| Delete | Lixeira (já existe `trash.png`) |

Assets sugeridos:

```
assets/unsend/textures/gui/reply.png      (16×16)
assets/unsend/textures/gui/edit.png       (16×16)
assets/unsend/textures/gui/trash.png      (já tem)
assets/unsend/textures/gui/trash_open.png (já tem)
```

---

## 8. Ordem de implementação

1. **Reply seta** topo-direita + citação no chat global  
2. **Edit** (lápis + ↑ última sua + `(editado)`)  
3. **Delete OP** (já tem dono; liberar `hasPermissions(2)` na UI + server — server já esboça OP)  
4. Atalho Shift+Delete  

---

## 9. Decisões fechadas

| # | Decisão |
|---|---------|
| Reply UI | Seta no **fim direito, em cima** da mensagem |
| Ícones | Premium, minimal |
| ↑ no input vazio | **Sim** — edita última mensagem sua |
| Delete próprio | Sim, para todos |
| Delete alheio | Só **admin/OP** |
| Reply canal | **Chat global** |
| Edit alheio | Não |

---

*Spec viva — atualizar se o fluxo de Reply com/sem Shift mudar na playtest.*
