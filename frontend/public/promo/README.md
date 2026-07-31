# Promo screenshots

Assets required for `/promo` (referenced by `frontend/promo.html`), not yet added:

- `screen-heatmap.png` — тепловая карта доступности
- `screen-availability.png` — отметка своего времени
- `screen-meeting.png` — создание встречи

Until these files exist, each slot renders the fallback «Скриншот скоро появится»
(the `<img>` removes itself via `onerror`), so the page stays presentable.

## How to capture

- **Тема:** тёмная (`ThemeToggle` в шапке приложения → тёмная тема)
- **Ширина окна:** десктоп ~1440px, зум браузера 100%
- **DPR:** снимать при DPR 2, затем даунскейл до **1600×1000**
- **Формат:** PNG, до **300KB** каждый

## Что не должно попасть в кадр

- Реальные email-адреса
- Реальные имена участников
- Браузерный хром (адресная строка, вкладки, панели расширений)

Use fictional display names and demo data when composing the shots.
