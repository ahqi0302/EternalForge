# EternalForge v3.4.0 

Paper 1.21.10 / Java 21 / MMOItems / MythicLib / Vault

## 新設定結構

```text
plugins/EternalForge/
├─ config.yml
├─ gui.yml
├─ messages.yml
├─ support-items.yml
├─ levels/
│  └─ default.yml
├─ recipes/
│  ├─ defaults/
│  │  ├─ swords.yml
│  │  └─ armor.yml
│  └─ swords/
│     └─ example_sword.yml
└─ history.log
```

`recipes/` 會遞迴掃描所有子資料夾，可自由新增 swords、bows、armor、boss 等分類。

## 配方優先順序

1. `TYPE:ID` 單一裝備
2. `TYPE:*` 該類型預設
3. `*:*` 全域預設

## 每把武器獨立設定

```yaml
item:
  type: SWORD
  id: FIRE_SWORD
enabled: true
max-level: 5
level-profile: default
materials:
  - 'MMOITEMS:MATERIAL:銅幣:2'
  - 'MMOITEMS:MATERIAL:FIRE_CRYSTAL:3'
levels:
  '1': { chance: 100.0, money: 100, fail-drop: 0, destroy: false }
  '2': { chance: 95.0, money: 200, fail-drop: 0, destroy: false }
  '5':
    chance: 70.0
    money: 1000
    fail-drop: 1
    destroy: false
    materials:
      - 'MMOITEMS:MATERIAL:銅幣:5'
      - 'MMOITEMS:MATERIAL:FIRE_CRYSTAL:5'
```

單一武器沒有寫的 level 會回退到 `level-profile`，再回退到 `levels/default.yml`。

## Reload

`/forge reload` 會重載：
- config.yml
- gui.yml
- messages.yml
- support-items.yml
- levels/*.yml
- recipes/**/*.yml
