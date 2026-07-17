# Unsend — Roadmap: Editar e Responder

Status atual (v0.1.0): **delete-for-everyone** + lixeira no Shift+hover + animação de sucção.

## 1. Editar mensagens

### UX
- Shift+hover na **sua** mensagem → além da lixeira, botão **lápis**
- Clique → preenche o `EditBox` do chat com o texto atual (sem prefixo `<nome>`)
- Enviar → atualiza a mensagem **para todos** (não cria outra linha)
- Mensagem editada ganha marcador cinza `(editado)` no final

### Técnico
| Peça | Detalhe |
|------|---------|
| Pacote C2S | `EditMessage(id, newText)` |
| Pacote S2C | `UpdateMessage(id, newText, editedAt)` |
| Server | Valida dono + rate-limit + tamanho max (256) |
| Client | Troca `GuiMessage.content` na lista `allMessages` + `refreshTrimmedMessage()` |
| Assinatura | Mensagens signed: editar quebra assinatura vanilla → usar **conteúdo unsigned** / re-render custom (aceito em SP e servidores com mod) |

### Riscos
- Chat signed (online mode) não permite “re-assinar” o mesmo id — solução: manter id nosso e reescrever só o HUD; log de denúncia vanilla fica com o original (ok)
- Compat com mods que mexem no chat HUD (WC, styled chat)

## 2. Responder (reply)

### UX
- Shift+hover em **qualquer** mensagem rastreada → botão **responder**
- Clique → input vira `↳ @Nome ` + foco no campo
- Ao enviar, a mensagem aparece como:
  ```
  <Você> ↳ Nome: trecho original…
  sua resposta aqui
  ```
  ou linha de citação compacta acima da resposta

### Técnico
| Peça | Detalhe |
|------|---------|
| Pacote C2S | opcional: `ReplyTo(id)` só para metadata; ou embutir no texto com marker invisível |
| S2C Register | incluir `replyToId` + `replyPreview` |
| Client render | se `replyToId != 0`, desenhar barra de citação acima da linha |

### Extra polish
- Clique na citação → scroll/highlight da mensagem original (se ainda existir)
- Se original foi apagada → citação vira `mensagem apagada`

## 3. Ordem de implementação sugerida

1. **Polish delete** — texture de lixeira PNG, som leve, config on/off  
2. **Edit** — pacotes + replace no HUD + badge `(editado)`  
3. **Reply** — UI botão + citação visual  
4. **1.19.2 port** — copiar scaffold Architectury como ATC/WC  
5. **Permissões** — op ops apagarem qualquer mensagem (já esboçado no tracker com `hasPermissions(2)`)

## 4. Notas de multiplayer

- Cliente **e** servidor precisam do mod
- Sem o mod no cliente: mensagem ainda é apagada se o servidor mandar S2C… mas só quem tem o mod anima/remove; clientes sem mod **não** removem (limite atual)
  - Mitigação futura: também enviar `ClientboundDeleteChatPacket` com signature quando existir

## 5. Nome de pastas no workspace

```
My-Mods-Professional/projects/ca/Unsend-1.20.1/
docs/ca/   (opcional: espelhar docs no docs/ do workspace)
```
